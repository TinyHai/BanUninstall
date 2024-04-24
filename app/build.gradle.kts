import org.gradle.kotlin.dsl.support.uppercaseFirstChar
import java.io.ByteArrayOutputStream

plugins {
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.jetbrainsKotlinAndroid)
    alias(libs.plugins.ksp)
    alias(libs.plugins.kotlinParcelize)
}

fun String.execute(): String {
    val byteOut = ByteArrayOutputStream()
    project.exec {
        commandLine = this@execute.split("\\s".toRegex())
        standardOutput = byteOut
    }
    return String(byteOut.toByteArray()).trim()
}

android {
    namespace = "cn.tinyhai.ban_uninstall"
    compileSdk = 34

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
        minSdk = 21
        targetSdk = 34
        versionCode = 4
        versionName = "1.2.1"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfig = signingConfigs.getByName("release")
            versionNameSuffix = "-${"git rev-parse --verify --short HEAD".execute()}"
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
        aidl = true
    }
    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.12"
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
}

afterEvaluate {
    android.applicationVariants.all {
        val buildType = buildType.name
        val flavor = flavorName
        val versionCode = versionCode
        val versionName = this.versionName
        val renameTaskName =
            "rename${flavor.uppercaseFirstChar()}${buildType.uppercaseFirstChar()}Output"
        tasks.register(renameTaskName) {
            val apkName = buildString {
                append("app")
                if (flavor.isNotBlank()) {
                    append("-$flavor")
                }
                append("-$buildType")
                append(".apk")
            }
            val newApkName = "${rootProject.name}_$versionName.apk"
            val apkPath = buildString {
                append(layout.buildDirectory.asFile.get().path)
                append("/outputs/apk/")
                if (flavor.isNotBlank()) {
                    append("$flavor/")
                }
                append(buildType)
            }
            val apkFile = File(apkPath, apkName)
            doLast {
                apkFile.renameTo(File(apkPath, newApkName))
                val versionCodeFile = File(apkPath, "versionCode")
                    .apply { if (!exists()) createNewFile() }
                versionCodeFile.bufferedWriter().use {
                    it.append(versionCode.toString())
                    it.flush()
                }
                val versionNameFile = File(apkPath, "versionName")
                    .apply { if (!exists()) createNewFile() }
                versionNameFile.bufferedWriter().use {
                    it.append(versionName)
                    it.flush()
                }
            }
        }
        tasks.findByName("assemble${flavor.uppercaseFirstChar()}${buildType.uppercaseFirstChar()}")
            ?.finalizedBy(renameTaskName)
    }
}

dependencies {
    compileOnly(libs.xposed.api)
    compileOnly(project(":hiddenApi"))
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.appcompat)
    implementation(libs.androidx.biometric)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.composeSettings.ui)
    implementation(libs.composeSettings.ui.extended)
    implementation(libs.dev.rikka.rikkax.parcelablelist)
    implementation(libs.coil.compose)
    implementation(libs.compose.destinations.core)
    implementation(libs.compose.destinations.bottomsheet)
    implementation("com.github.TinyHai:ComposeDragDrop:dev-SNAPSHOT")
    implementation(project(":hook"))
    ksp(project(":processor"))
    ksp(libs.compose.destinations.ksp)
    debugImplementation(libs.androidx.compose.ui.tooling)
}