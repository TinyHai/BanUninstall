@file:OptIn(KspExperimental::class)

import com.android.build.api.variant.impl.VariantOutputImpl
import com.google.devtools.ksp.KspExperimental
import org.gradle.kotlin.dsl.support.uppercaseFirstChar
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.ksp)
    alias(libs.plugins.kotlinParcelize)
    alias(libs.plugins.composeCompiler)
}

fun String.execute(): String =
    ProcessBuilder(this.split("\\s".toRegex()))
        .start()
        .inputStream
        .bufferedReader()
        .use { it.readText() }
        .trim()

android {
    namespace = "cn.tinyhai.ban_uninstall"
    compileSdk = 37

    signingConfigs {
        register("release") {
            storeFile = file("../keystore.jks")
            storePassword = System.getenv("KEYSTORE_PASSWORD")
            keyAlias = System.getenv("KEY_ALIAS")
            keyPassword = System.getenv("KEY_PASSWORD")
        }
    }

    defaultConfig {
        applicationId = "cn.tinyhai.ban_uninstall"
        minSdk = 23
        targetSdk = 37
        versionCode = 13
        versionName = "1.5.1"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        debug {
            versionNameSuffix = "-${"git rev-parse --verify --short HEAD".execute()}_debug"
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfig = signingConfigs.getByName("release")
            versionNameSuffix = "-${"git rev-parse --verify --short HEAD".execute()}"
        }
    }

    packaging {
        jniLibs {
            useLegacyPackaging = true
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
        aidl = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    ksp.useKsp2 = true
    enableKotlin = true
}

kotlin {
    compilerOptions {
        jvmTarget = JvmTarget.JVM_17
    }
}

androidComponents {
    onVariants {
        val buildType = it.buildType ?: ""
        val flavor = it.flavorName ?: ""
//        val versionCode = versionCode
//        val versionName = this.versionName
        for (output in it.outputs) {
            val apkName = "${rootProject.name}_${output.versionName.get()}.apk"
            (output as VariantOutputImpl).outputFileName = apkName
        }
        val apkPath = buildString {
            append(layout.buildDirectory.asFile.get().path)
            append("/outputs/apk/")
            append(buildType)
        }
        afterEvaluate {
            tasks.named("assemble${flavor.uppercaseFirstChar()}${buildType.uppercaseFirstChar()}") {
                it.outputs.forEach { output ->
                    doLast {
                        val versionCodeFile = File(apkPath, "versionCode")
                            .apply { if (!exists()) createNewFile() }
                        versionCodeFile.bufferedWriter().use {
                            it.append(output.versionCode.get().toString())
                            it.flush()
                        }
                        val versionNameFile = File(apkPath, "versionName")
                            .apply { if (!exists()) createNewFile() }
                        versionNameFile.bufferedWriter().use {
                            it.append(output.versionName.get())
                            it.flush()
                        }
                    }
                }
            }
        }
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    compileOnly(libs.xposed.api)
    compileOnly(project(":hiddenApi"))
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.appcompat)
    implementation(libs.androidx.biometric)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.dev.rikka.rikkax.parcelablelist)
    implementation(libs.coil.compose)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.viewmodel.navigation3)
    implementation(libs.miuix.ui)
    implementation(libs.androidx.navigation3.runtime)
    implementation(libs.miuix.navigation3.ui)
    implementation(libs.miuix.icons)
    implementation(libs.miuix.preference)
    implementation(libs.androidx.navigationevent.compose)
    implementation(project(":hook"))
    ksp(project(":processor"))
    ksp(libs.compose.destinations.ksp)
    debugImplementation(libs.androidx.compose.ui.tooling)
}