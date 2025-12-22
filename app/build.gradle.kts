plugins {
    alias(libs.plugins.android.application)
}

android {
    namespace = "com.example.circlelauncher"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.example.circlelauncher"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

dependencies {
    implementation(mapOf("name" to "SettingsLibIconLoaderLib-15.0.13", "ext" to "aar"))
    implementation(libs.appcompat)
    implementation(libs.activity)
    implementation(libs.constraintlayout)
}