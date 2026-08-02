plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

val modelCatalogGenerator = rootProject.file("scripts/generate_android_model_catalog.py")
val modelCatalogOverrides = rootProject.file("scripts/android_model_catalog_overrides.json")
val desktopModelCatalog = rootProject.file("src-tauri/src/catalog/catalog.json")
val generatedModelCatalog = file("src/main/java/com/handy/android/ModelCatalog.kt")

val generateModelCatalog = tasks.register<Exec>("generateModelCatalog") {
    commandLine("python3", modelCatalogGenerator.absolutePath)
    inputs.files(desktopModelCatalog, modelCatalogOverrides, modelCatalogGenerator)
    outputs.file(generatedModelCatalog)
}

val checkModelCatalog = tasks.register<Exec>("checkModelCatalog") {
    commandLine("python3", modelCatalogGenerator.absolutePath, "--check")
    inputs.files(desktopModelCatalog, modelCatalogOverrides, modelCatalogGenerator, generatedModelCatalog)
    mustRunAfter(generateModelCatalog)
}

tasks.named("preBuild") {
    dependsOn(checkModelCatalog)
}

afterEvaluate {
    tasks.named("testDebugUnitTest") {
        dependsOn(checkModelCatalog)
    }
}

android {
    namespace = "com.handy.android"
    compileSdk = 35
    ndkVersion = "27.0.12077973"

    lint {
        abortOnError = true
        warningsAsErrors = true
        checkAllWarnings = true
        checkDependencies = true
        disable += listOf(
            "MissingTranslation",
            "GoogleAppIndexingWarning",
            "OldTargetApi",
            "GradleDependency",
            "NewerVersionAvailable",
            "ObsoleteSdkInt",
            "UnusedResources",
            "SyntheticAccessor",
            "ClickableViewAccessibility",
            "SetTextI18n",
            "InlinedApi",
            // Compose lint detector bundled with AGP 8.7 crashes with Compose 1.11/Kotlin 2.2
            // (IncompatibleClassChangeError in ComposableFlowOperatorDetector).
            "FlowOperatorInvokedInComposition",
            // Informational: suggests optional core-ktx extensions; not a correctness issue.
            "UseKtx",
            // Informational: newer Gradle/AGP availability notices.
            "AndroidGradlePluginVersion",
        )
    }

    defaultConfig {
        applicationId = "com.handy.android"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0"

        externalNativeBuild {
            cmake {
                cppFlags += listOf("-std=c++17", "-Wall", "-Wextra")
            }
        }

        ndk {
            abiFilters += setOf("arm64-v8a", "armeabi-v7a", "x86", "x86_64")
        }
    }

    testOptions {
        unitTests {
            isIncludeAndroidResources = true
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlin {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
            version = "3.22.1"
        }
    }

    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2026.06.01")
    implementation(composeBom)
    androidTestImplementation(composeBom)

    implementation("androidx.activity:activity-compose:1.10.0")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    // Material 3 adaptive support (Google I/O 2026): window size classes + navigation suite.
    implementation("androidx.compose.material3:material3-window-size-class")
    implementation("androidx.compose.material3:material3-adaptive-navigation-suite")
    // Not managed by the 2026 Compose BOM; icons used by settings rows and app bars.
    implementation("androidx.compose.material:material-icons-core:1.7.8")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
    // 1.24.x ships 16 KB-aligned native libraries (required by Android 15+ devices).
    implementation("com.microsoft.onnxruntime:onnxruntime-android:1.24.3")

    debugImplementation("androidx.compose.ui:ui-tooling")

    testImplementation("junit:junit:4.13.2")
    testImplementation("androidx.test:core:1.6.1")
    testImplementation("org.robolectric:robolectric:4.14.1")
    // 2.6.1 ships linux-aarch64 natives; 2.5.2 (transitively used by Robolectric) is x86_64-only.
    testImplementation("org.conscrypt:conscrypt-openjdk-uber:2.6.1")
}
