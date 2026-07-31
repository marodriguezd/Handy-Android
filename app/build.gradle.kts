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

android {
    namespace = "com.handy.android"
    compileSdk = 35
    ndkVersion = "27.0.12077973"

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

    kotlinOptions {
        jvmTarget = "17"
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
    val composeBom = platform("androidx.compose:compose-bom:2024.12.01")
    implementation(composeBom)
    androidTestImplementation(composeBom)

    implementation("androidx.activity:activity-compose:1.10.0")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")

    debugImplementation("androidx.compose.ui:ui-tooling")

    testImplementation("junit:junit:4.13.2")
}
