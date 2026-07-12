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
    val buildFlag = if (isRelease) "--release" else ""
    commandLine(
        "cargo", "ndk",
        "--target", "aarch64-linux-android",
        "--platform", "26",
        "--",
        "-p", "handy-core",
        "build", buildFlag
    )
}

val rustLibFile = file("${rootDir}/handy-core/target/aarch64-linux-android/release/libhandy_core.so")
val jniLibDir = file("src/main/jniLibs/arm64-v8a")

val copyRustLib by tasks.registering(Copy::class) {
    dependsOn(buildRust)
    from(rustLibFile)
    into(jniLibDir)
}

tasks.whenTaskAdded {
    if (name.startsWith("merge") && name.endsWith("JniLibFolders")) {
        dependsOn(copyRustLib)
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

    implementation(libs.shizuku.api)
    implementation(libs.shizuku.provider)

    implementation(libs.sentry.android)
}
