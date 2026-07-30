# ONNX Runtime's JNI resolves these Java types by their original names.
-keep class ai.onnxruntime.** { *; }
-keep class ai.onnxruntime.extensions.** { *; }

# LiteRT-LM's native bridge resolves callbacks and message classes through JNI.
-keep class com.google.ai.edge.litertlm.** { *; }
