plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.hilt.android)
    alias(libs.plugins.ksp)
}

android {
    namespace = "com.streammind.feature.aiqa"
    compileSdk = 34

    defaultConfig {
        minSdk = 26
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    implementation(project(":core:core-common"))
    // `api`, not `implementation`: RetrievedChunk exposes TranscriptSegmentEntity
    // in its public signature, so consumers (the player UI) need it on their
    // compile classpath too.
    api(project(":data:data-local"))
    implementation(project(":data:data-remote"))

    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.gson)
    implementation(libs.retrofit)

    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}
