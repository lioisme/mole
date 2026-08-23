plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.ksp) apply false
    // V22 R2:开源许可元数据自动生成(aboutlibraries,行业标准方案)。
    alias(libs.plugins.aboutlibraries.plugin) apply false
}