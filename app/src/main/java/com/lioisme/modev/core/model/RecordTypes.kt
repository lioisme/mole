package com.lioisme.modev.core.model

/**
 * 记录类型标识(V10 能力化架构;V17 数据库监听扩展):
 * 存储与线协议以字符串承载,缺失时按 [CRYPTO] 解释(旧数据/旧协议向后兼容)。
 * 未来行为监听、交互监听、文件监听等记录类型按此扩展。
 */
object RecordTypes {

    /** 算法捕获记录(JCA 调用)。 */
    const val CRYPTO = "crypto"

    /** 数据库操作记录(SQLiteDatabase / SQLiteOpenHelper / SQLiteStatement / SQLiteCursor)。 */
    const val DATABASE = "db"

    /** 行为监听记录(文件系统 / Assets / Shell,V27)。 */
    const val BEHAVIOR = "behavior"

    /** SharedPreferences 监听记录(读入配置文件 + Get/Put 全家桶,V28)。 */
    const val PREFS = "prefs"

    /** WebView 监听记录(页面加载/JS 执行/桥注册/调试/JS 密码学,V29 一等公民)。 */
    const val WEBVIEW = "webview"

    /** 网络环境监听记录(SSL 绕过/代理隐藏/VPN 隐藏/请求监控,V30 一等公民)。 */
    const val NETWORK = "network"

    /** 设备环境监听记录(Root 隐藏/Xposed 隐藏/模拟器隐藏/检测监控,V31 一等公民)。 */
    const val DEVICE_ENV = "device_env"

    /** UI 交互监听记录(弹窗/控件文本/Activity 跳转,V33 一等公民)。 */
    const val UI_INTERACTION = "ui"

    /** 系统信息监听记录(Logcat/App 元数据/签名,V33 一等公民)。 */
    const val SYSTEM = "system"

    /** 进程控制监听记录(退出拦截/崩溃防止/录屏绕过,V33 一等公民)。 */
    const val PROCESS_CONTROL = "process"
}
