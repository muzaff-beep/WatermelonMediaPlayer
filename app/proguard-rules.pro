# app/proguard-rules.pro
# Watermelon MediaPlayer ProGuard rules

# Keep JNI methods
-keepclasseswithmembernames class com.watermelon.player.rust.WatermelonCore {
    native <methods>;
}

# Keep callback interface
-keep interface com.watermelon.player.rust.WatermelonEventCallback { *; }

# Keep Room entities
-keep class com.watermelon.player.database.** { *; }

# Keep data classes
-keep class com.watermelon.player.repository.Playlist { *; }
-keep class com.watermelon.player.repository.PlaylistEntry { *; }
-keep class com.watermelon.player.viewmodel.PlayerState { *; }
-keep class com.watermelon.player.viewmodel.FolderItem { *; }

# FFmpeg
-keep class com.arthenica.ffmpegkit.** { *; }
-dontwarn com.arthenica.ffmpegkit.**