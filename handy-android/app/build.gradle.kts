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
        versionCode = 5
        versionName = "0.2.0-preview"  // Second pre-release (Sprint 17-23 + pre-Sprint 24 hygiene, 17 Julio 2026)

        // Sentry DSN — override via SENTRY_DSN env var (used in CI)
        buildConfigField("String", "SENTRY_DSN", "\"${System.getenv("SENTRY_DSN") ?: "https://examplePublicKey@o0.ingest.sentry.io/0"}\"")

        // TestCommandReceiver is enabled only in debug builds.
        manifestPlaceholders["debugReceiverEnabled"] = "true"
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
            // Disable the test-only receiver in release builds.
            manifestPlaceholders["debugReceiverEnabled"] = "false"
        }
        debug {
            isMinifyEnabled = false
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
            manifestPlaceholders["debugReceiverEnabled"] = "true"
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

    // Suppress known-false-positive `ObsoleteSdkInt` warnings for
    // `mipmap-anydpi-v26` (the conventional location for <adaptive-icon>
    // XMLs on API 26+ devices). Context documented in `lint.xml`.
    // Sprint 29spa Phase 2: MissingTranslation is disabled because values-es/ exists
    // as a partial Spanish locale override (only 20 keys of 256 are mirrored).
    // Full i18n coverage for es-locale users is deferred to a future sprint. Until
    // then, es-locale users see the English default locale values for all keys
    // except the 20 translated in Phase 2.
    lint {
        disable += "ObsoleteSdkInt"
        disable += "MissingTranslation"
    }

    // Pre-Sprint-26 Batch C: RecordingRepository's hot path and the
    // HistoryViewModel retry-fallback path log via android.util.Log on
    // rare failure. JVM unit tests run without Robolectric, so
    // stubbed Log methods would throw `Method ... not mocked.` by
    // default. `isReturnDefaultValues = true` makes the stubbed
    // methods return platform defaults (0 / null / false) instead —
    // the standard Android Kotlin pattern for pure-JVM test source-
    // sets. Production builds are unaffected.
    testOptions {
        unitTests.isReturnDefaultValues = true
        unitTests.isIncludeAndroidResources = true
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
    // SPIRV-Headers and Vulkan-Headers are vendored because the build host may
    // not have the system packages installed (required when GGML_VULKAN=ON).
    // Vulkan is only enabled for release builds to keep debug iteration fast and
    // avoid requiring a fully configured Vulkan toolchain during development.
    val spirvHeadersDir = file("${rootDir}/deps/spirv-install")
    val spirvCmakeDir = file("${spirvHeadersDir}/share/cmake/SPIRV-Headers")
    val vulkanHeadersDir = file("${rootDir}/deps/Vulkan-Headers")
    val vulkanInclude = "${vulkanHeadersDir}/include"
    val spirvInclude = "${spirvHeadersDir}/include"
    val commonFlags = "-I${vulkanInclude} -I${spirvInclude}"
    val vulkanArgs = if (isRelease) {
        "-DGGML_VULKAN=ON -DSPIRV-Headers_DIR=${spirvCmakeDir.absolutePath} -DCMAKE_CXX_FLAGS=${commonFlags} -DCMAKE_C_FLAGS=${commonFlags}"
    } else {
        "-DGGML_VULKAN=OFF"
    }
    environment("TRANSCRIBE_CMAKE_ARGS", "-DGGML_NATIVE=OFF ${vulkanArgs}")
    environment("CMAKE_ARGS", "-DCMAKE_TOOLCHAIN_FILE=${ndkDir}/build/cmake/android.toolchain.cmake -DANDROID_ABI=arm64-v8a -DANDROID_PLATFORM=android-24 -DGGML_NATIVE=OFF ${vulkanArgs}")
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
    implementation(libs.material)
    implementation("androidx.compose.material:material-icons-extended")
    implementation(libs.compose.ui.tooling.preview)
    debugImplementation(libs.compose.ui.tooling)

    implementation(libs.activity.compose)
    implementation(libs.lifecycle.viewmodel.compose)
    implementation(libs.navigation.compose)
    implementation(libs.coroutines.android)
    implementation(libs.lifecycle.runtime)
    // Sprint 29c — foldable hinge avoidance. WindowInfoTracker exposes a
    // Flow<WindowLayoutInfo> that MainActivity.kt collects via produceState
    // and emits as FoldingFeatureInfo state passed to AppNavigation.kt.
    // The Flow auto-cancels when the Compose lifecycle ends (process death,
    // Activity destroy). The pure presentation logic is JVM-testable in
    // FoldPresentation.kt without needing WindowInfoTracker on the
    // test classpath.
    implementation(libs.androidx.window)

    // Sprint 23 — About/Theme/Locale picker uses AppCompatDelegate
    // setApplicationLocales() which needs androidx.appcompat. core-ktx
    // brings LocaleListCompat used to express the BCP-47 list.
    implementation(libs.appcompat)
    implementation(libs.core.ktx)

    implementation(libs.shizuku.api)
    implementation(libs.shizuku.provider)

    implementation(libs.sentry.android)

    // Unit tests (pure JVM, no Robolectric)
    testImplementation(libs.junit)
    testImplementation(libs.json.test)

    // Sprint 28b-v13 — Compose UI test infra for DebugLayoutRegressionTest.
    // `createComposeRule()` requires `ui-test-junit4` + a hosting
    // ComponentActivity injected by `ui-test-manifest` at debug variant
    // time. Robolectric 4.14.1 supports API 35 + JDK 17 + AGP 8.8.2 cleanly.
    // androidx-test-ext-junit (= `@RunWith(AndroidJUnit4::class)`) and
    // androidx-test-core (`ApplicationProvider` etc.) are the paired
    // surfaces. Production builds are unaffected — all five entries live
    // in test-only / debug-only configurations.
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.ext.junit)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.compose.ui.test.junit4)
    debugImplementation(libs.compose.ui.test.manifest)
}
