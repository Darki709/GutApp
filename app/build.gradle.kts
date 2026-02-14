plugins {
    alias(libs.plugins.android.application)
}

android {
    namespace = "com.example.gutapp"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.example.gutapp"
        minSdk = 27
        targetSdk = 36
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
    //including the charting library dependency

    implementation(libs.mpandroidchart)
    implementation(libs.appcompat)
    implementation(libs.material)
    implementation(libs.activity)
    implementation(libs.constraintlayout)
    testImplementation(libs.junit)
    androidTestImplementation(libs.ext.junit)
    androidTestImplementation(libs.espresso.core)
    implementation("androidx.security:security-crypto:1.1.0")
    implementation("androidx.core:core-splashscreen:1.2.0")
    val sqlite_version = "2.6.2"
    // This is the "Magic" library that includes the modern SQLite 3.24+ C++ engine
    implementation("androidx.sqlite:sqlite-bundled:$sqlite_version")
    // Core interface for the bundled driver
    implementation("androidx.sqlite:sqlite:$sqlite_version")
}
