package com.lioisme.modev.core.model

/**
 * 行为监听类别(V27):文件系统 / Assets / Shell 三域,粒度对齐用户可感知操作。
 *
 * 默认启用态与 [CategoryTaxonomy] 噪点分级一致(决策 2026-08-26):
 * - 高信号默认开:写/删/重命名/Shell(低频高价值);
 * - 高噪声默认关:读/列目录/Assets(App 启动即触发数百次,冲刷 20k 记录上限)。
 */
enum class BehaviorKind {
    /** 文件读取(FileInputStream/FileReader/Files.newInputStream/readAllBytes 等)。 */
    FILE_READ,

    /** 文件写入(FileOutputStream/Files.newOutputStream/write/copy/mkdir 族)。 */
    FILE_WRITE,

    /** 文件删除(File.delete / Files.delete)。 */
    FILE_DELETE,

    /** 文件重命名/移动(File.renameTo / Files.move)。 */
    FILE_RENAME,

    /** 目录列举(File.list/listFiles / Files.list / AssetManager.list)。 */
    FILE_LIST,

    /** Assets 资源读取(AssetManager.open/openFd/list)。 */
    ASSET_READ,

    /** Shell 命令执行(Runtime.exec / ProcessBuilder.start)。 */
    SHELL_EXEC,
}
