plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
}

android {
    namespace = "com.handy.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.handy.app"
        minSdk = 26
        targetSdk = 35
        versionCode = 2
        versionName = "1.0.0-alpha1"

        // Sentry DSN — override via SENTRY_DSN env var (used in CI)
        buildConfigField("String", "SENTRY_DSN", "\"${System.getenv("SENTRY_DSN") ?: "https://examplePublicKey@o0.ingest.sentry.io/0"}\"")
    }

    signingConfigs {
        create("release") {
            storeFile = file(System.getenv("HANDY_KEYSTORE_PATH") ?: "../handy-release.keystore")
            storePassword = System.getenv("HANDY_KEYSTORE_PASSWORD") ?: ""
            keyAlias = System.getenv("HANDY_KEY_ALIAS") ?: "handy"
            keyPassword = System.getenv("HANDY_KEY_PASSWORD") ?: ""
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            signingConfig = signingConfigs.getByName("release")
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        debug {
            isMinifyEnabled = false
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
        buildConfig = true
        aidl = true
    }

    composeOptions {
        kotlinCompilerExtensionVersion = libs.versions.compose.compiler.get()
    }
}

val buildRust by tasks.registering(Exec::class) {
    description = "Build Rust native library via cargo-ndk for aarch64-linux-android"
    workingDir = file("${rootDir}/handy-core")
    val isRelease = gradle.startParameter.taskNames.any {
        it.contains("release", ignoreCase = true)
    }
    environment("CARGO_NDK_LINK_LIBCXX_SHARED", "true")
    val ndkDir = android.ndkDirectory.absolutePath
    environment("ANDROID_NDK_HOME", ndkDir)
    environment("CMAKE_TOOLCHAIN_FILE", "${ndkDir}/build/cmake/android.toolchain.cmake")
    environment("TRANSCRIBE_CMAKE_ARGS", "-DGGML_NATIVE=OFF")
    environment("CMAKE_ARGS", "-DCMAKE_TOOLCHAIN_FILE=${ndkDir}/build/cmake/android.toolchain.cmake -DANDROID_ABI=arm64-v8a -DANDROID_PLATFORM=android-24 -DGGML_NATIVE=OFF")
    if (isRelease) {
        commandLine("cargo", "ndk", "--target", "aarch64-linux-android", "--platform", "26", "--link-libcxx-shared", "--", "build", "-p", "handy-core", "--release")
    } else {
        commandLine("cargo", "ndk", "--target", "aarch64-linux-android", "--platform", "26", "--link-libcxx-shared", "--", "build", "-p", "handy-core")
    }
}

val isRelease = gradle.startParameter.taskNames.any {
    it.contains("release", ignoreCase = true)
}
val buildProfile = if (isRelease) "release" else "debug"
val rustLibFile = file("${rootDir}/handy-core/target/aarch64-linux-android/$buildProfile/libhandy_core.so")
val jniLibDir = file("src/main/jniLibs/arm64-v8a")

val copyRustLib by tasks.registering(Copy::class) {
    dependsOn(buildRust)
    from(rustLibFile)
    into(jniLibDir)
}

val copyLibCxx by tasks.registering(Copy::class) {
    description = "Copy libc++_shared.so from NDK into jniLibs"
    val ndkDir = android.ndkDirectory
    from("$ndkDir/toolchains/llvm/prebuilt/linux-x86_64/sysroot/usr/lib/aarch64-linux-android/libc++_shared.so")
    into(jniLibDir)
}

tasks.whenTaskAdded {
    if (name.startsWith("merge") && name.endsWith("JniLibFolders")) {
        dependsOn(copyRustLib)
        dependsOn(copyLibCxx)
    }
}

dependencies {
    val composeBom = platform(libs.compose.bom)
    implementation(composeBom)
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.material3)
    implementation("androidx.compose.material:material-icons-extended")
    implementation(libs.compose.ui.tooling.preview)
    debugImplementation(libs.compose.ui.tooling)

    implementation(libs.activity.compose)
    implementation(libs.lifecycle.viewmodel.compose)
    implementation(libs.navigation.compose)
    implementation(libs.coroutines.android)
    implementation(libs.lifecycle.runtime)

    implementation(libs.shizuku.api)
    implementation(libs.shizuku.provider)

    implementation(libs.sentry.android)

    // Unit tests (pure JVM, no Robolectric)
    testImplementation(libs.junit)
    testImplementation(libs.json.test)
}
