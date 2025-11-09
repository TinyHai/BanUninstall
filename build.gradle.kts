import org.gradle.kotlin.dsl.support.uppercaseFirstChar

// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
    alias(libs.plugins.androidApplication) apply false
    alias(libs.plugins.jetbrainsKotlinAndroid) apply false
    alias(libs.plugins.androidLibrary) apply false
    alias(libs.plugins.composeCompiler) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.jetbrainsKotlinJvm) apply false
    alias(libs.plugins.kotlinParcelize) apply false
}

ext["allArch"] = emptyArray<String>()

val allArch: Array<String> by ext

tasks.register("buildRelease") {
    val allBuildType = allArch + "release"
    allBuildType.forEach {  buildType ->
        dependsOn(":app:assemble${buildType.uppercaseFirstChar()}")
    }
}

tasks.register("buildDebug") {
    dependsOn(":app:assembleDebug")
}