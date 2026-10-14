plugins {
    alias(libs.plugins.android.library)
}

android {
    namespace = "app.folio.core.layout"
    compileSdk = 37

    defaultConfig {
        minSdk = 34
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    api(project(":core:model"))
    implementation(libs.androidx.core.ktx)
    testImplementation(libs.junit)
}
