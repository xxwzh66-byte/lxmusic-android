# Keep LX source engine classes
-keep class com.lxmusic.android.jsengine.** { *; }
-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}
