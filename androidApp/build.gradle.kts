plugins {
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
}

kotlin {
    androidTarget()
    sourceSets {
        androidMain.dependencies {
            implementation(project(":shared"))
            implementation(libs.activity.compose)
        }
    }
}

android {
    namespace = "dev.dwm.liftlog"
    compileSdk = 36
    defaultConfig {
        applicationId = "dev.dwm.liftlog"
        minSdk = 31
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0"
    }
    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}
