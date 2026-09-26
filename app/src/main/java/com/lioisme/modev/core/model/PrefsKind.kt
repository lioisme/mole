package com.lioisme.modev.core.model

/**
 * SharedPreferences 监听类别:按用户可感知操作分五类。
 *
 * 默认启用态与 [CategoryTaxonomy] 噪点分级一致(决策 D4):
 * - 高信号默认开:OPEN(读入配置文件,进程内每文件一次)/ PUT / REMOVE / CLEAR;
 * - 高噪声默认关:GET(App 每次配置读取均触发,冲刷记录上限)。
 */
enum class PrefsKind {
    /** 读入配置文件(SharedPreferencesImpl 构造完成 = 磁盘载入时刻)。 */
    OPEN,

    /** 配置读取(get* 全家桶 / contains / getAll)。 */
    GET,

    /** 配置写入(editor.put* 全家桶;暂存即记,commit/apply 不挂钩——决策 D5)。 */
    PUT,

    /** 单键删除(editor.remove)。 */
    REMOVE,

    /** 全量清空(editor.clear)。 */
    CLEAR,
}
