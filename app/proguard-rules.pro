# Keep line numbers useful in release crash reports.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# The terminal library is reached through JNI and through interfaces implemented on the Java side.
# R8 cannot see either edge, so it strips the classes and the app dies with NoClassDefFoundError the
# moment a session starts. The native method names in libtermux.so are also bound to these exact
# class and method names, so they cannot be renamed.
-keep class com.termux.terminal.** { *; }
-keep class com.termux.view.** { *; }
-keepclassmembers class com.termux.terminal.** { *; }
-keepclassmembers class com.termux.view.** { *; }

# The native library looks these up by name.
-keepclasseswithmembernames class com.termux.terminal.JNI {
    native <methods>;
}

# The manifest names this class, and it is instantiated by the platform.
-keep class com.termfold.app.TermFoldApp { *; }
-keep class com.termfold.app.MainActivity { *; }

# The terminal's colour table is a public static that the emulator reads reflectively.
-keep class com.termux.terminal.TerminalColors { *; }
-keep class com.termux.terminal.TerminalColorScheme { *; }

# GeckoView JNI and reflection.
-keep class org.mozilla.geckoview.** { *; }
-keep class org.mozilla.gecko.** { *; }
-dontwarn org.mozilla.**

# Built-in gecko-termfold bridge + preview engine: Gecko calls session/extension delegates from
# native code; R8 must not strip or rename them (release-only crashes when opening browser).
-keep class com.termfold.app.preview.** { *; }
-keep class com.termfold.app.preview.engine.** { *; }
-keepclassmembers class * implements org.mozilla.geckoview.GeckoSession$ProgressDelegate { *; }
-keepclassmembers class * implements org.mozilla.geckoview.GeckoSession$ContentDelegate { *; }
-keepclassmembers class * implements org.mozilla.geckoview.GeckoSession$NavigationDelegate { *; }
-keepclassmembers class * implements org.mozilla.geckoview.GeckoSession$HistoryDelegate { *; }
-keepclassmembers class * implements org.mozilla.geckoview.WebExtension$MessageDelegate { *; }
-keepclassmembers class * implements org.mozilla.geckoview.WebExtension$PortDelegate { *; }

# SnakeYAML (transitive) references java.beans, which Android does not ship.
-dontwarn java.beans.BeanInfo
-dontwarn java.beans.FeatureDescriptor
-dontwarn java.beans.IntrospectionException
-dontwarn java.beans.Introspector
-dontwarn java.beans.PropertyDescriptor

# The file editor's page calls back into these through addJavascriptInterface.
-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}
-keepattributes JavascriptInterface

# Release builds drop debug and verbose logging. Agent stderr is logged at debug level and can
# contain tokens or file contents; it also costs time on slow phones.
-assumenosideeffects class android.util.Log {
    public static int v(...);
    public static int d(...);
}
