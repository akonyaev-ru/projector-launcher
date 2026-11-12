plugins {
    id("com.android.application")
}

android {
    namespace = "pro.icover.vitrina"
    compileSdk = 34

    defaultConfig {
        applicationId = "pro.icover.vitrina"
        minSdk = 28
        // Проектор — Android TV 13 (API 33): targetSdk равен версии устройства.
        targetSdk = 33
        versionCode = 2
        versionName = "0.1.1"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            // Личная сборка для одного устройства: подписываем отладочным ключом.
            signingConfig = signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    lint {
        // Приложение не для Google Play: требование магазина к targetSdk не применяется.
        disable += "ExpiredTargetSdkVersion"
    }
}

dependencies {
    testImplementation("junit:junit:4.13.2")
}
