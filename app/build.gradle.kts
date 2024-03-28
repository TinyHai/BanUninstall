plugins {
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.jetbrainsKotlinAndroid)
}

android {
    namespace = "cn.tinyhai.ban_uninstall"
    compileSdk = 34

    defaultConfig {
        applicationId = "cn.tinyhai.ban_uninstall"
        minSdk = 21
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfig = getByName("debug").signingConfig
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
        aidl = true
    }
    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.2"
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
    kotlinOptions {
        jvmTarget = "1.8"
    }
}

dependencies {
    compileOnly(libs.xposed.api)
    compileOnly(project(":hiddenApi"))
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.composeSettings.ui)
    implementation(libs.composeSettings.ui.extended)
    implementation(libs.dev.rikka.rikkax.parcelablelist)
    debugImplementation(libs.androidx.compose.ui.tooling)
}