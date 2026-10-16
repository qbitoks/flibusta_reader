# sherpa-onnx JNI reads config fields and builds GeneratedAudio by name: keep the Kotlin side intact.
-keep class com.k2fsa.sherpa.onnx.** { *; }
# onnxruntime's JNI builds OnnxTensor/OrtSession results by name (Silero).
-keep class ai.onnxruntime.** { *; }
