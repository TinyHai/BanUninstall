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

val allArch: Array<String> by rootProject.ext

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
        versionCode = 7
        versionName = "1.3.2"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        debug {
            versionNameSuffix = "-${"git rev-parse --verify --short HEAD".execute()}_debug"
        }
        release {
            isMinifyEnabled = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfig = signingConfigs.getByName("release")
            versionNameSuffix = "-${"git rev-parse --verify --short HEAD".execute()}"
        }

        allArch.forEach { arch ->
            register(arch) {
                initWith(getByName("release"))
                versionNameSuffix = "${versionNameSuffix}_$arch"
                ndk {
                    abiFilters.clear()
                    abiFilters += if (arch == "universal") arrayOf(
                        "arm64-v8a",
                        "armeabi-v7a",
                        "x86",
                        "x86_64"
                    ) else arrayOf(arch)
                }
            }
        }

        all {
            matchingFallbacks += "release"
            val fieldValue = when (this.name) {
                "debug", in allArch -> "true"
                else -> "false"
            }
            buildConfigField("boolean", "ROOT_FEATURE", fieldValue)
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

        val renameTask =
            tasks.register("rename${flavor.uppercaseFirstChar()}${buildType.uppercaseFirstChar()}Output") {
                doLast {
                    apkFile.renameTo(File(apkPath, newApkName))
                }
            }

        val assembleTask =
            tasks.findByName("assemble${flavor.uppercaseFirstChar()}${buildType.uppercaseFirstChar()}")
        assembleTask?.finalizedBy(renameTask)

        when (buildType) {
            "release" -> {
                assembleTask?.doLast {
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

                tasks.findByName("strip${buildType.uppercaseFirstChar()}DebugSymbols")?.doLast {
                    file(this.outputs.files.asPath).let { if (it.exists()) it.deleteRecursively() }
                }

                tasks.findByName("merge${flavor.uppercaseFirstChar()}${buildType.uppercaseFirstChar()}Assets")
                    ?.doLast {
                        val lspatchDir = file(this.outputs.files.asPath).resolve("lspatch")
                        if (lspatchDir.exists()) {
                            lspatchDir.deleteRecursively()
                        }
                    }
            }

            in allArch -> {
                tasks.findByName("merge${flavor.uppercaseFirstChar()}${buildType.uppercaseFirstChar()}Assets")
                    ?.doLast {
                        val soDir = file(this.outputs.files.asPath).resolve("lspatch").resolve("so")
                        soDir.listFiles()?.filter { it.isDirectory }?.forEach {
                            if (it.name != buildType) {
                                it.deleteRecursively()
                            }
                        }
                    }
            }

            "universal", "debug" -> {}
        }
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
    implementation(libs.libsu.core)
    implementation("com.github.TinyHai:ComposeDragDrop:dev-SNAPSHOT")
    implementation(project(":hook"))
    ksp(project(":processor"))
    ksp(libs.compose.destinations.ksp)
    debugImplementation(libs.androidx.compose.ui.tooling)
}