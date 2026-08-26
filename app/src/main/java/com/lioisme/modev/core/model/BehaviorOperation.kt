package com.lioisme.modev.core.model

/**
 * 行为监听操作(V27):具体 API 动作,[kind] 归属到类别。
 * kind = 用户开关粒度([HookSwitch.behaviorEnabled] 门控),
 * operation = 列表元行/详情页展示粒度。
 */
enum class BehaviorOperation(
    val kind: BehaviorKind,
) {
    /** 打开输入流(FileInputStream/FileReader 构造器、Files.newInputStream)。 */
    OPEN_READ(BehaviorKind.FILE_READ),

    /** 整体读取(Files.readAllBytes / readAllLines)。 */
    READ_ALL(BehaviorKind.FILE_READ),

    /** 打开输出流(FileOutputStream 构造器、Files.newOutputStream;append 入 params)。 */
    OPEN_WRITE(BehaviorKind.FILE_WRITE),

    /** 整体写出(Files.write / Files.copy 到目标路径)。 */
    WRITE_ALL(BehaviorKind.FILE_WRITE),

    /** 创建单级目录(File.mkdir / Files.createDirectory)。 */
    MKDIR(BehaviorKind.FILE_WRITE),

    /** 创建多级目录(File.mkdirs / Files.createDirectories)。 */
    MKDIRS(BehaviorKind.FILE_WRITE),

    /** 删除(File.delete、Files.delete)。 */
    DELETE(BehaviorKind.FILE_DELETE),

    /** 重命名/移动(File.renameTo、Files.move;目标入 destPath)。 */
    RENAME(BehaviorKind.FILE_RENAME),

    /** 列举目录(File.list/listFiles、Files.list;条目数入 params.dirCount)。 */
    LIST(BehaviorKind.FILE_LIST),

    /** Assets 打开流(AssetManager.open;accessMode 入 params)。 */
    ASSET_OPEN(BehaviorKind.ASSET_READ),

    /** Assets 打开 FileDescriptor(AssetManager.openFd;长度/偏移入 params)。 */
    ASSET_OPEN_FD(BehaviorKind.ASSET_READ),

    /** Assets 列举(AssetManager.list)。 */
    ASSET_LIST(BehaviorKind.ASSET_READ),

    /** Shell 命令执行(Runtime.exec / ProcessBuilder.start;api 入 params 区分)。 */
    EXEC(BehaviorKind.SHELL_EXEC),
}
