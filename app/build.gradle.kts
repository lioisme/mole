import java.util.Properties
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

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
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    // V22 R2:开源许可元数据生成(aboutlibraries;Android 模块自动注册生成任务,
    // 产物落入 res/raw/aboutlibraries.json,随变体构建自动更新)。
    alias(libs.plugins.aboutlibraries.plugin)
}

// 拉取 SPDX 许可全文并内嵌(离线后 UI 回退显示 SPDX 标识;许可全文属法律合规数据,
// 内容为规范权威文本,构建期拉取一次即缓存)。
aboutLibraries {
    collect {
        fetchRemoteLicense = true
    }
}

android {
    namespace = "com.lioisme.modev"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.lioisme.modev"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        if (hasSigning) {
            create("release") {
                storeFile = rootProject.file(keystoreProps.getProperty("storeFile"))
                storePassword = keystoreProps.getProperty("storePassword")
                keyAlias = keystoreProps.getProperty("keyAlias")
                keyPassword = keystoreProps.getProperty("keyPassword")
            }
            getByName("debug") {
                storeFile = rootProject.file(keystoreProps.getProperty("debugStoreFile"))
                storePassword = keystoreProps.getProperty("debugStorePassword")
                keyAlias = keystoreProps.getProperty("debugKeyAlias")
                keyPassword = keystoreProps.getProperty("debugKeyPassword")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            if (hasSigning) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
        debug {
            if (hasSigning) {
                signingConfig = signingConfigs.getByName("debug")
            }
        }
    }

    // 双框架构建变体:
    //  - modern(默认):META-INF/xposed/java_init.list + module.prop(minApiVersion=102),供 Vector 现代加载
    //  - legacy:仅 assets/xposed_init + module.prop(minApiVersion=82),供 LSPosed ≤93 / EdXposed / 1.10.x legacy 路径加载
    flavorDimensions += "framework"
    productFlavors {
        create("modern") {
            dimension = "framework"
        }
        create("legacy") {
            dimension = "framework"
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }

    splits {
        abi {
            isEnable = true
            reset()
            include("arm64-v8a", "x86_64")
            isUniversalApk = false
        }
    }

    testOptions {
        unitTests {
            // Robolectric:Room DAO / AppCatalog 映射测试需要 Android 框架影子实现
            isIncludeAndroidResources = true
            // 纯 JVM 单测中 android.util.Log 等桩调用返回默认值而非抛 Stub! 异常
            isReturnDefaultValues = true
        }
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

ksp {
    // Room schema 基线导出,供迁移测试与审计比对
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.activity.compose)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    // HCT 色板算法(materialkolor,LIDE 同版本同源):主题引擎唯一色彩来源
    implementation(libs.com.materialkolor)

    implementation(libs.androidx.navigation.compose)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.androidx.paging.runtime)
    implementation(libs.androidx.paging.compose)
    // 详情页 markdown 渲染(mikepenz,M3 构件;Apache-2.0;V26 版本锁定见 toml)
    implementation(libs.markdown.renderer.m3)
    // V28:语法高亮引擎(SnipMeDev Highlights,纯 KMP 直依赖;json→JS 分词、bash→SHELL)
    implementation(libs.highlights)
    // 开源许可数据 core(编译期元数据,零运行时开销;UI 自建 M3 页面)
    implementation(libs.aboutlibraries.core)

    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    implementation(libs.androidx.room.paging)
    ksp(libs.androidx.room.compiler)

    implementation(libs.gson)
    // V38:MCP 服务 HTTP 传输引擎(见 toml 注释;Agent 面向能力,与 ConfigServer wire 协议完全独立)
    implementation(libs.coil.compose)
    implementation(libs.nanohttpd)

    compileOnly(libs.libxposed.api)
    // 框架通信服务(现代入口宿主进程使用;legacy 变体仅编译期引用,运行时缺失→框架桥自然降级)
    "modernImplementation"(libs.libxposed.service)
    "legacyCompileOnly"(libs.libxposed.service)
    // legacy Xposed API 82(旧版 LSPosed ≤93 注入入口编译用,运行时由框架提供)
    compileOnly(libs.xposed.api)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.robolectric)
    // V26:Robolectric 下 Compose 冒烟(markdown 渲染库运行时验证)
    testImplementation(libs.androidx.compose.ui.test.junit4)
      testImplementation(libs.androidx.compose.ui.test.manifest)
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
