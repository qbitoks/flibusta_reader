plugins { id("com.android.application"); kotlin("android") }
android {
    namespace = "net.flibustareader"
    compileSdk = 34
    defaultConfig {
        applicationId = "net.flibustareader"
        minSdk = 34
        targetSdk = 34
        versionCode = 3
        versionName = "0.2.1-reader-design"
    }
    buildFeatures { compose = true }
    composeOptions { kotlinCompilerExtensionVersion = "1.5.14" }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
        isCoreLibraryDesugaringEnabled = true
    }
    kotlinOptions { jvmTarget = "17" }
}
dependencies {
    implementation(project(":core"))
    implementation(platform("androidx.compose:compose-bom:2024.06.00"))
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.activity:activity-compose:1.9.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.3")
    implementation("androidx.fragment:fragment-ktx:1.8.1")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
    implementation("org.readium.kotlin-toolkit:readium-shared:3.0.0")
    implementation("org.readium.kotlin-toolkit:readium-streamer:3.0.0")
    implementation("org.readium.kotlin-toolkit:readium-navigator:3.0.0")
    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:2.0.4")
}
