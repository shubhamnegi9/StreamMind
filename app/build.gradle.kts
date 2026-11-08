import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.hilt.android)
    alias(libs.plugins.ksp)
}

val localProperties = Properties().apply {
    val file = rootProject.file("local.properties")
    if (file.exists()) load(file.inputStream())
}

android {
    namespace = "com.streammind.app"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.streammind.app"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "0.1.0"

        buildConfigField(
            "String",
            "GEMINI_API_KEY",
            "\"${localProperties.getProperty("GEMINI_API_KEY", "")}\""
        )
    }

    buildTypes {
        debug {
            isDebuggable = true
        }
        release {
            isMinifyEnabled = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    // USE_FAKE_AI lives on this flavor dimension (not build type) so you can flip
    // fakeDebug <-> liveDebug from the Build Variants panel in Android Studio in two
    // clicks — handy right before a live coding round or recording a demo on flaky wifi.
    flavorDimensions += "network"
    productFlavors {
        create("fake") {
            dimension = "network"
            buildConfigField("boolean", "USE_FAKE_AI", "true")
        }
        create("live") {
            dimension = "network"
            buildConfigField("boolean", "USE_FAKE_AI", "false")
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    implementation(project(":core:core-common"))
    implementation(project(":feature:player"))
    implementation(project(":feature:transcript"))
    implementation(project(":feature:ai-chapters"))
    implementation(project(":feature:ai-qa"))
    implementation(project(":feature:accessibility"))
    implementation(project(":data:data-local"))
    implementation(project(":data:data-remote"))

    implementation(libs.core.ktx)
    implementation(libs.core.splashscreen)
    implementation(libs.lifecycle.runtime.ktx)
    implementation(libs.activity.compose)
    implementation(libs.navigation.compose)

    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.foundation)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    debugImplementation(libs.compose.ui.tooling)

    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.hilt.navigation.compose)
}
