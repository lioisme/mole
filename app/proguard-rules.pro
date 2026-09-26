# Modev: 算法捕获 Xposed 模块
# 本模块为目标 App 动态捕获全部算法调用(摘要/加解密/签名/MAC/密钥/编码),需配合宿主 App 查看实时日志。
# 双框架兼容:现代入口(ModevModule,libxposed 102)+ legacy 入口(LegacyModuleEntry,API 82+)。

-keep class com.lioisme.modev.ModevModule { *; }
-keep class com.lioisme.modev.LegacyModuleEntry { *; }
-keep class com.lioisme.modev.core.** { *; }
-keep class com.lioisme.modev.hook.** { *; }
-dontwarn io.github.libxposed.**
-dontwarn de.robv.android.xposed.**
