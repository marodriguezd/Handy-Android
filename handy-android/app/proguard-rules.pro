# ── Handy JNI Bridge — keep all native method host classes ──────

# EngineBridge: all external (native) methods must keep their exact names
-keep class com.handy.app.bridge.EngineBridge {
    native <methods>;
    <init>();
}

# EngineCallback: keep all methods so Rust can call them via reflection
-keep interface com.handy.app.bridge.EngineCallback {
    *;
}

# EngineCallback implementations must keep all methods
-keep class * implements com.handy.app.bridge.EngineCallback {
    *;
}

# ── General ──────────────────────────────────────────────────────

# Shizuku — suppress hidden API warnings in our code
-dontwarn android.os.ServiceManager
-dontwarn android.hardware.input.IInputManager

# Shizuku SDK
-keep class moe.shizuku.api.** { *; }

# HandyUserService AIDL — protect interface and generated inner classes
-keep class com.handy.app.injection.IHandyUserService { *; }
-keep class com.handy.app.injection.IHandyUserService$Stub { *; }
-keep class com.handy.app.injection.IHandyUserService$Proxy { *; }
-keep class com.handy.app.injection.HandyUserService { *; }

# Keep Compose-related metadata
-dontwarn androidx.compose.**
-keep class androidx.compose.** { *; }

# Keep kotlin coroutines internals (needed by StateFlow in ViewModel)
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}

# ── Debugging ────────────────────────────────────────────────────

# Keep source file and line numbers for crash reports
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
