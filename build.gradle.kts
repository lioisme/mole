plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.ksp) apply false
    // V22 R2:开源许可元数据自动生成(aboutlibraries,行业标准方案)。
    alias(libs.plugins.aboutlibraries.plugin) apply false
}

// V44 T1.5 纪律:单测任务挂上限时护栏——协程死锁(如 runBlocking 占住主线程 +
// Main 派发上游永不推进)会让任务无限挂起、CI 静默超时且无失败证据;上限把
// 「挂起」转成「有界失败」,与各测试自身的轮询 deadline 形成双保险。
tasks.withType<Test>().configureEach {
    timeout.set(java.time.Duration.ofMinutes(45))
}