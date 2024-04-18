plugins {
    alias(libs.plugins.androidLibrary)
}

android {
    namespace = "cn.tinyhai.hiddenapi"
    compileSdk = 34

    lint {
        abortOnError = false
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
}