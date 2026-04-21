import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi
import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.androidLibrary)
}

kotlin {
    applyDefaultHierarchyTemplate()

    androidTarget {
        @OptIn(ExperimentalKotlinGradlePluginApi::class)
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_1_8)
        }
    }

    sourceSets.commonMain.dependencies {
        api(compose.runtime)
        api(compose.foundation)
        api(compose.ui)
        api(compose.material)
        api(compose.material3)
        api(compose.materialIconsExtended)
        @OptIn(org.jetbrains.compose.ExperimentalComposeLibrary::class)
        implementation(compose.components.resources)

        implementation(projects.richeditorCompose)
        implementation(projects.richeditorComposeCoil3)

        // Coil
        implementation(libs.coil.compose)
        implementation(libs.coil.svg)
        implementation(libs.coil.network.ktor)

        // Ktor
        implementation(libs.ktor.client.core)

        // Lifecycle
        implementation(libs.lifecycle.viewmodel.compose)

        // Navigation
        implementation(libs.navigation.compose)
    }

    sourceSets.androidMain.dependencies {
        api(libs.androidx.appcompat)

        implementation(libs.kotlinx.coroutines.android)
        implementation(libs.ktor.client.okhttp)
    }
}

android {
    namespace = "com.mohamedrejeb.richeditor.sample.common"
    compileSdk = libs.versions.android.compileSdk.get().toInt()
    sourceSets["main"].resources.srcDirs("src/commonMain/resources")

    defaultConfig {
        minSdk = libs.versions.android.minSdk.get().toInt()
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
}