plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.handy.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.handy.app"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
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
    }

    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.14"
    }
}

val buildRust by tasks.registering(Exec::class) {
    description = "Build Rust native library via cargo-ndk for aarch64-linux-android"
    workingDir = file("${rootDir}/handy-core")
    commandLine(
        "cargo", "ndk",
        "--target", "aarch64-linux-android",
        "--platform", "26",
        "--",
        "-p", "handy-core",
        "build", "--release"
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
    val composeBom = platform("androidx.compose:compose-bom:2025.01.00")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.ui:ui-tooling-preview")
    debugImplementation("androidx.compose.ui:ui-tooling")

    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.navigation:navigation-compose:2.8.5")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
}
