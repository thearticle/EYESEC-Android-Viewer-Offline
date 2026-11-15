# Proguard rules for EyesecViewer

# Keep RTSP Client models & native callbacks
-keep class com.alexvas.rtsp.** { *; }
-keep interface com.alexvas.rtsp.** { *; }

# Keep data classes used in ViewModel
-keep class com.eyesec.viewer.data.** { *; }
