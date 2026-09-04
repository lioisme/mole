import java.util.Properties

val keystorePropsFile = rootProject.file("keystore/keystore.properties")
val keystoreProps = Properties().apply {
    if (keystorePropsFile.exists()) {
        keystorePropsFile.inputStream().use { load(it) }
    }
}
val hasSigning = keystorePropsFile.exists()

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
}

android {
    namespace = "com.lioisme.modev.testtarget"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.lioisme.modev.testtarget"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "35.0"
    }

    signingConfigs {
        if (hasSigning) {
            getByName("debug") {
                storeFile = rootProject.file(keystoreProps.getProperty("debugStoreFile"))
                storePassword = keystoreProps.getProperty("debugStorePassword")
                keyAlias = keystoreProps.getProperty("debugKeyAlias")
                keyPassword = keystoreProps.getProperty("debugKeyPassword")
            }
        }
    }

    buildTypes {
        debug {
            isMinifyEnabled = false
        }
        release {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlin {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
        }
    }
}

dependencies {
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
}
