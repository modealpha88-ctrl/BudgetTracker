plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}
android {
    namespace = "com.example.mibbudget"
    compileSdk = 35
    defaultConfig { applicationId = "com.example.mibbudget"; minSdk = 26; targetSdk = 35; versionCode = (System.getenv("GITHUB_RUN_NUMBER")?.toIntOrNull() ?: 1000); versionName = "0.2" }
    signingConfigs {
        create("mibRelease") {
            storeFile = file(System.getenv("MIB_RELEASE_KEYSTORE_PATH") ?: "missing-release-keystore.jks")
            storePassword = System.getenv("MIB_RELEASE_STORE_PASSWORD")
            keyAlias = System.getenv("MIB_RELEASE_KEY_ALIAS")
            keyPassword = System.getenv("MIB_RELEASE_KEY_PASSWORD")
        }
    }
    buildTypes {
        getByName("release") {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("mibRelease")
        }
    }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures { compose = true }
}
dependencies {
    testImplementation("junit:junit:4.13.2")
    implementation(platform("androidx.compose:compose-bom:2024.12.01"))
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    implementation("com.google.mlkit:text-recognition:16.0.1")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.ui:ui-tooling-preview")
    debugImplementation("androidx.compose.ui:ui-tooling")
}
