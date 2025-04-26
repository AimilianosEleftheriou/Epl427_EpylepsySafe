plugins {
    alias(libs.plugins.android.application)
}

android {
    namespace = "com.med.health_app"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.med.health_app"
        minSdk = 24
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

    implementation(libs.appcompat)
    implementation(libs.material)
    implementation(libs.preference)
    testImplementation(libs.junit)
    androidTestImplementation(libs.ext.junit)
    androidTestImplementation(libs.espresso.core)
    implementation ("com.google.android.material:material:1.9.0")

    implementation ("com.github.anastr:SpeedView:1.5.0")
    implementation ("com.github.PhilJay:MPAndroidChart:v3.1.0")

    implementation ("androidx.work:work-runtime:2.9.0")
}