"""Builds Folio's Silero voice model: Silero v5 CIS base (nostress, MIT) as int8 ONNX.

The TorchScript release does not export to ONNX as is, so this re-implements it in eager PyTorch with
the original weights (layer code mirrors the TorchScript .code), checks it matches, exports and quantizes.
Pause tokens (negative ids) and speaker mixing are left out; `speed` divides phoneme durations linearly.

    python -m venv venv && venv/Scripts/pip install torch onnx onnxruntime onnxscript
    curl -LO https://models.silero.ai/models/tts/ru/v5_cis_base_nostress.jit
    venv/Scripts/python -I scripts/silero/export_onnx.py v5_cis_base_nostress.jit silero-v5-cis-base-int8.onnx

Inputs: text [1, L] int64 (ids from `core/speech/.../SileroVoices.kt`), speaker [1] int64, speed float.
Output: audio [N] float, 48 kHz mono.
"""
import math, sys, torch
import torch.nn as nn
import torch.nn.functional as F


def conv(s):
    c = nn.Conv1d(s.in_channels, s.out_channels, s.kernel_size[0], padding=s.padding[0], groups=s.groups)
    c.load_state_dict(s.state_dict()); return c

def lin(s):
    l = nn.Linear(s.in_features, s.out_features, bias=s.bias is not None)
    l.load_state_dict(s.state_dict()); return l

def ln(s):
    n = nn.LayerNorm(s.normalized_shape, eps=s.eps)
    n.load_state_dict(s.state_dict()); return n

def emb(s):
    e = nn.Embedding(s.num_embeddings, s.embedding_dim)
    e.load_state_dict(s.state_dict()); return e


class Attn(nn.Module):
    """Batch-first self-attention without masks (export-friendly MHA)."""
    def __init__(self, s):
        super().__init__()
        self.h, self.d = s.num_heads, s.head_dim
        self.w = nn.Parameter(s.in_proj_weight.detach().clone())
        self.b = nn.Parameter(s.in_proj_bias.detach().clone())
        self.out = lin(s.out_proj)

    def forward(self, x):
        B, L, E = x.shape
        q, k, v = F.linear(x, self.w, self.b).chunk(3, -1)
        sh = lambda t: t.reshape(B, L, self.h, self.d).transpose(1, 2)
        a = torch.softmax(sh(q) @ sh(k).transpose(-1, -2) / math.sqrt(self.d), -1) @ sh(v)
        return self.out(a.transpose(1, 2).reshape(B, L, E))


class FFTBlock(nn.Module):
    def __init__(self, s):
        super().__init__()
        assert not s.use_linear and not s.pitch_pred
        self.attn, self.norm1, self.norm2 = Attn(s.self_attn), ln(s.norm1), ln(s.norm2)
        self.conv1, self.conv2 = conv(s.conv1), conv(s.conv2)

    def forward(self, x):
        x = self.norm1(x + self.attn(x)).transpose(1, 2)
        x = x + self.conv2(F.relu(self.conv1(x)))
        return self.norm2(x.transpose(1, 2))


class PosEnc(nn.Module):
    def __init__(self, s):
        super().__init__()
        self.scale = float(s.scale)
        self.register_buffer('pe', s.pe.detach().clone())  # [max_len, 1, dim]

    def forward(self, x):  # [B, L, D]
        return x + self.scale * self.pe[:x.shape[1], 0].unsqueeze(0)


class Stack(nn.Module):
    """ForwardTransformer: pos-enc, FFT blocks, final norm."""
    def __init__(self, s):
        super().__init__()
        self.pos = PosEnc(s.pos_encoder)
        self.layers = nn.ModuleList(FFTBlock(l) for _, l in s.layers.named_children())
        self.norm = ln(s.norm)

    def forward(self, x):
        x = self.pos(x)
        for l in self.layers:
            x = l(x)
        return self.norm(x)


class SeriesPredictor(nn.Module):
    def __init__(self, s):
        super().__init__()
        self.emb, self.spk = emb(s.embedding), emb(s.speaker_embedding)
        self.tr, self.lin = Stack(s.transformer), lin(s.lin)

    def forward(self, text, speaker):
        x = self.emb(text) + self.spk(speaker).unsqueeze(1)
        return self.lin(self.tr(x))


class MelEncoder(nn.Module):
    def __init__(self, s):
        super().__init__()
        assert not s.articulation_emb
        self.emb, self.spk = emb(s.embedding), emb(s.speaker_embedding)
        self.enc, self.pitch_proj = Stack(s.encoder), conv(s.pitch_proj)
        self.strength = float(s.pitch_strength)

    def forward(self, text, speaker, pitch):  # pitch [B, 1, L]
        x = self.enc(self.emb(text)) + self.spk(speaker).unsqueeze(1)
        return x, self.pitch_proj(pitch).transpose(1, 2) * self.strength


class HourGlass(nn.Module):
    def __init__(self, s):
        super().__init__()
        self.pos = PosEnc(s.pos_encoder)
        self.pre = FFTBlock(getattr(s.pre_vanilla_layers, "0"))
        self.k = int(s.shorten_factor)
        self.short1 = FFTBlock(getattr(s.shorten_layers, "1"))  # layer 0's output is discarded upstream
        self.up, self.dim = lin(s.upsample.proj), int(s.upsample.dim)
        self.post, self.norm = FFTBlock(getattr(s.post_vanilla_layers, "0")), ln(s.norm)

    def forward(self, x):
        L = x.shape[1]
        x = self.pre(self.pos(x))
        x = F.pad(x, (0, 0, 0, (-L) % self.k))
        d = F.avg_pool1d(x.transpose(1, 2), self.k, self.k).transpose(1, 2)
        u = self.up(self.short1(d)).reshape(x.shape[0], -1, self.dim)
        return self.norm(self.post((u + x)[:, :L]))


class ConvNeXt(nn.Module):
    def __init__(self, s):
        super().__init__()
        self.dw, self.norm = conv(s.dwconv), ln(s.norm)
        self.pw1, self.pw2 = lin(s.pwconv1), lin(s.pwconv2)
        self.gamma = nn.Parameter(s.gamma.detach().clone())

    def forward(self, x):
        y = self.pw2(F.gelu(self.pw1(self.norm(self.dw(x).transpose(1, 2)))))
        return x + (self.gamma * y).transpose(1, 2)


class Vocoder(nn.Module):
    """Vocos-style backbone + ISTFT head; iSTFT as real matmul + fold."""
    def __init__(self, s, ist):
        super().__init__()
        b = s.backbone
        self.embed, self.norm = conv(b.embed), ln(b.norm)
        self.blocks = nn.ModuleList(ConvNeXt(c) for _, c in b.convnext.named_children())
        self.final, self.out = ln(b.final_layer_norm), lin(s.head.out)
        n_fft, self.hop, self.win = ist.n_fft, ist.hop_length, ist.win_length
        self.pad = (self.win - self.hop) // 2
        assert self.win % self.hop == 0
        bins = n_fft // 2 + 1
        k = torch.arange(bins, dtype=torch.float64)[:, None]
        n = torch.arange(n_fft, dtype=torch.float64)[None, :]
        ang = 2 * math.pi * k * n / n_fft
        w = torch.full((bins, 1), 2.0, dtype=torch.float64); w[0] = 1.0
        if n_fft % 2 == 0: w[-1] = 1.0
        win = ist.window.double()[None, :]
        self.register_buffer('cos_b', (w * torch.cos(ang) / n_fft * win).float())  # [bins, n_fft]
        self.register_buffer('sin_b', (-w * torch.sin(ang) / n_fft * win).float())
        self.register_buffer('win_sq', ist.window.float() ** 2)

    def forward(self, mel):  # [B, 192, T]
        x = self.norm(self.embed(mel).transpose(1, 2)).transpose(1, 2)
        for b in self.blocks:
            x = b(x)
        mag, p = self.out(self.final(x.transpose(1, 2))).chunk(2, -1)  # [B, T, bins]
        mag = torch.clamp(torch.exp(mag), max=100.0)
        frames = (mag * torch.cos(p)) @ self.cos_b + (mag * torch.sin(p)) @ self.sin_b  # [B, T, n_fft]
        return self.overlap_add(frames[0], self.win_sq.expand_as(frames[0]))

    def overlap_add(self, frames, wsq):  # [T, win]; win is an exact multiple of hop
        r = self.win // self.hop
        T = frames.shape[0]
        y = env = None
        for j in range(r):
            f = F.pad(frames[:, j * self.hop:(j + 1) * self.hop], (0, 0, j, r - 1 - j))
            w = F.pad(wsq[:, j * self.hop:(j + 1) * self.hop], (0, 0, j, r - 1 - j))
            y = f if y is None else y + f
            env = w if env is None else env + w
        y, env = y.reshape(-1), env.reshape(-1)
        size = (T - 1) * self.hop + self.win
        return (y / torch.clamp(env, min=1e-11))[self.pad:size - self.pad]


class SileroTTS(nn.Module):
    def __init__(self, m):
        super().__init__()
        self.dur = SeriesPredictor(m.dur_predictor.dur_pred)
        self.pitch = SeriesPredictor(m.pitch_predictor.pitch_pred)
        self.enc = MelEncoder(m.mel_encoder)
        self.dec, self.dec_lin = HourGlass(m.mel_decoder.decoder), lin(m.mel_decoder.lin)
        self.voc = Vocoder(m.vocoder, m.istft)

    def forward(self, text, speaker, speed):
        """text [1, L] int64, speaker [1] int64, speed scalar float (1 = normal) -> audio [N] @48 kHz."""
        frames = torch.exp(self.dur(text, speaker).squeeze(-1)) - 1
        dur = torch.clamp(torch.round(frames / speed), min=0).long()[0]
        dur = torch.cat([torch.clamp(dur[:1], max=5), dur[1:]])
        pitch = self.pitch(text, speaker).transpose(1, 2)
        enc, pproj = self.enc(text, speaker, pitch)
        x = torch.repeat_interleave(enc[0] + pproj[0], dur, 0).unsqueeze(0)
        mel = self.dec_lin(self.dec(x)).transpose(1, 2)
        return self.voc(mel)


if __name__ == '__main__':
    import os
    from onnxruntime.quantization import QuantType, quantize_dynamic
    src, dst = sys.argv[1], sys.argv[2]
    m = torch.jit.load(src).eval()
    e = SileroTTS(m).eval()
    x = torch.tensor([[2] + list(range(20, 60)) + [1]])
    s = torch.LongTensor([26])
    fp32 = dst + '.fp32.onnx'
    with torch.no_grad():
        ref, got = m(x, s).reshape(-1), e(x, s, torch.tensor(1.0))
        assert ref.shape == got.shape and (ref - got).abs().max() < 1e-2, 'eager model differs from TorchScript'
        torch.onnx.export(e, (x, s, torch.tensor(1.0)), fp32,
                          input_names=['text', 'speaker', 'speed'], output_names=['audio'],
                          dynamic_axes={'text': {1: 'n'}, 'audio': {0: 't'}},
                          opset_version=18, dynamo=False)
    # int8 weights everywhere except the iSTFT basis, whose precision is audible.
    quantize_dynamic(fp32, dst, weight_type=QuantType.QInt8, op_types_to_quantize=['MatMul', 'Gemm'],
                     nodes_to_exclude=['/voc/MatMul', '/voc/MatMul_1'])
    os.remove(fp32)
    print(dst, os.path.getsize(dst), 'bytes')
