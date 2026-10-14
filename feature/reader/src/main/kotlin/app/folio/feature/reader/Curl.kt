package app.folio.feature.reader

import android.graphics.RenderEffect
import android.graphics.RuntimeShader
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asComposeRenderEffect
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

/**
 * «Загиб» (white spots 11–12, frames on #63): the page wraps round a cylinder whose axis runs
 * slightly askew; the back of the sheet shows the print faintly mirrored through the paper, the
 * cylinder darkens towards its far side, and the page underneath gets a soft shadow.
 *
 * The shader deforms the page layer's own picture (`content`), so nothing is rendered to bitmaps.
 * For each output pixel it finds the topmost layer of paper above it — the flat back, the upper
 * half of the cylinder, its lower half, the flat front — and samples the unrolled page there.
 */
@RequiresApi(33)
internal class PageCurl {
    private val shader = RuntimeShader(SHADER)

    /**
     * Effect for a page turned by [turned] (0 flat … 1 gone) of a [width]×[height] layer.
     * [anchorY] is where the finger went down; it tilts the fold like a corner being lifted.
     */
    fun effect(turned: Float, width: Float, height: Float, anchorY: Float, paper: Color, dark: Boolean): androidx.compose.ui.graphics.RenderEffect {
        val radius = width * 0.11f
        val angle = ((anchorY / height).coerceIn(0f, 1f) - 0.5f) * 0.24f
        val slack = height * abs(sin(angle))
        val start = width + slack
        val end = -(2.4f * radius + slack)
        val fold = start + (end - start) * turned.coerceIn(0f, 1f)
        shader.setFloatUniform("size", width, height)
        shader.setFloatUniform("fold", fold, anchorY)
        shader.setFloatUniform("dir", cos(angle), sin(angle))
        shader.setFloatUniform("radius", radius)
        shader.setFloatUniform("paper", paper.red, paper.green, paper.blue, 1f)
        shader.setFloatUniform("shadow", if (dark) 0.5f else 0.28f)
        return RenderEffect.createRuntimeShaderEffect(shader, "content").asComposeRenderEffect()
    }

    companion object {
        /** The deformation needs AGSL; older systems fall back to «Скольжение». */
        val supported: Boolean get() = Build.VERSION.SDK_INT >= 33

        private const val SHADER = """
uniform shader content;
uniform float2 size;
uniform float2 fold;
uniform float2 dir;
uniform float radius;
uniform float4 paper;
uniform float shadow;

const float PI = 3.14159265;

bool inPage(float2 p) {
    return p.x >= 0.0 && p.y >= 0.0 && p.x <= size.x && p.y <= size.y;
}

// Distance along dir from xy to where it leaves the page (the right edge of the unrolled sheet).
float toEdge(float2 xy) {
    float tx = dir.x > 0.0001 ? (size.x - xy.x) / dir.x : 1e6;
    float ty = dir.y > 0.0001 ? (size.y - xy.y) / dir.y : (dir.y < -0.0001 ? -xy.y / dir.y : 1e6);
    return min(tx, ty);
}

half4 main(float2 xy) {
    float d = dot(xy - fold, dir);
    // Back of the sheet: the flat part laid back over the page, then the cylinder's upper half.
    if (d <= radius) {
        float s = d >= 0.0 ? PI * radius - radius * asin(d / radius) : PI * radius - d;
        float2 src = xy + (s - d) * dir;
        if (inPage(src)) {
            float4 c = float4(content.eval(src));
            float3 back = mix(c.rgb, paper.rgb, 0.82);
            float light = d >= 0.0 ? 0.80 + 0.20 * (1.0 - d / radius) : 1.0;
            return half4(half3(back * light), 1.0);
        }
    }
    // Front of the sheet going round the cylinder's lower half.
    if (d >= 0.0 && d <= radius) {
        float s = radius * asin(d / radius);
        float2 src = xy + (s - d) * dir;
        if (inPage(src)) {
            float4 c = float4(content.eval(src));
            float f = d / radius;
            return half4(half3(c.rgb * (1.0 - 0.30 * f * f)), half(c.a));
        }
    }
    if (d < 0.0) {
        // Flat front; darkened just beyond the edge of the flap lying over it.
        float4 c = float4(content.eval(xy));
        float over = (PI * radius - d) - (d + toEdge(xy));
        float k = over > 0.0 ? shadow * 0.6 * (1.0 - clamp(over / (radius * 0.5), 0.0, 1.0)) : 0.0;
        return half4(half3(c.rgb * (1.0 - k)), half(c.a));
    }
    // Uncovered: the next page shows through, shaded near the curl.
    float t = clamp((d - radius) / (radius * 1.3), 0.0, 1.0);
    return half4(0.0, 0.0, 0.0, half(shadow * (1.0 - t) * (1.0 - t)));
}
"""
    }
}
