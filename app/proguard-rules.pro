# JavaScript bridge methods are called reflectively from the WebView.
-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}
-keepattributes JavascriptInterface

# Crash reports (CrashLog) should name the real classes and lines. The code is open source, so
# renaming it hides nothing; shrinking still removes unused code.
-dontobfuscate
-keepattributes SourceFile,LineNumberTable
