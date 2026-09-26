package com.lioisme.modev.data.db

import android.content.Context
import android.util.Log
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * 宿主侧数据库。单进程单例,避免多实例竞争。
 *
 * v1 → v2:新增 salt/aad/publicKey 双态视图四元组(各 4 列)、paramsJson、verifyResult、processName。
 * v2 → v3:新增 seq 列(注入侧进程级单调序号)与 (packageName, processName, seq) 唯一索引,
 *   断线重放幂等(REPLACE 吸收重复);存量行 seq 为 NULL,不参与唯一性冲突。
 * v3 → v4:新增 recordType 列(记录类型维度,默认 crypto),存量行迁移后为 'crypto'。
 * v4 → v5:新增 timestamp 单列索引(时间倒序热路径:全局裁剪/每包裁剪/邻接/分页)。
 * v5 → v6:数据库监听(T17)新增 8 个可空列:sql / dbPath / bindArgsJson / rowsJson /
 *   rowCount / affectedCount / rowId / resultText,recordType='db' 行承载。
 * v6 → v7:三态视图(V19)7 个字节字段各增 Base64 列(keyB64/ivB64/inputB64/outputB64/
 *   saltB64/aadB64/publicKeyB64),存量行 NULL = 旧记录降级双态。
 * v7 → v8:多维筛选复合索引([recordType]+[kind]、[recordType]+[packageName]+[timestamp]),
 *   纯 CREATE INDEX,不触碰存量数据(与实体声明索引命名一致,运行时 schema 校验通过)。
 * v8 → v9:行为监听(V27)新增 10 个可空列:path / destPath / command / fileSize +
 *   content 五元组(contentHex/contentB64/contentText/contentLength/contentTruncated),
 *   recordType='behavior' 行承载;纯 ALTER TABLE,不触碰存量行(与 v5→v6 同先例)。
 * v9 → v10:偏好监听(V28)新增 1 个可空列 prefKey(配置键),
 *   recordType='prefs' 行承载(值域复用 content 五元组、路径复用 path 列);
 *   纯 ALTER TABLE ADD COLUMN,不触碰存量行。
 * v10 → v11:网络环境监听(V30)新增 11 个可空列:url / method / statusCode /
 *   requestHeadersJson / responseHeadersJson / sslProtocol / cipherSuite /
 *   peerCertificatesJson / proxyHost / proxyPort / vpnActive,
 *   recordType='network' 行承载;纯 ALTER TABLE ADD COLUMN,不触碰存量行。
 * v11 → v12:V33 三大类监听新增 27 个可空列:
 *   UI 交互(11):dialogTitle / dialogMessage / dialogKeywords / dialogIntercepted /
 *     widgetText / widgetClassName / activityName / intentAction / intentData /
 *     intentExtrasJson / intentFlags;
 *   系统信息(10):logcatTag / logcatLevel / logcatMessage / appVersion / appVersionCode /
 *     appMinSdk / appTargetSdk / appPermissions / signatureHash / signatureBase64;
 *   进程控制(6):exitCode / processPid / crashType / crashMessage / crashBlocked /
 *     screenRecordBlocked;
 *   recordType='ui'/'system'/'process' 行承载;纯 ALTER TABLE ADD COLUMN,不触碰存量行。
 * v12 → v13:V34 审计增强新增 3 个可空列:
 *   interventionAction(TEXT,干预动作标记:"BLOCKED"/"DETECTED"/null);
 *   同时覆盖进程控制/设备环境/UI交互三类记录;纯 ALTER TABLE ADD COLUMN,不触碰存量行。
 * v13 → v14:V37 T2.1 功能域数据层新增 `keyword_library` 表
 *   (全局弹窗拦截关键词库:id/text 唯一/matchMode/enabled/createdAt),
 *   纯 CREATE TABLE + UNIQUE INDEX,不触碰存量记录。
 * v14 → v15:V47 T2 设备画像重建两件事同版本落地:
 *   新增 `spoof_profile` 表(画像库:profileJson 正文 + 内置档账本列 + 删除墓碑,
 *   builtinSeedId 建普通索引);`crypto_records` 新增可空列 `maskedValue`
 *   (干预后的值,与 proxyHost 承载的原值配对成双侧 oracle),纯 ADD COLUMN 不触碰存量行。
 */
@Database(
    entities = [CryptoRecordEntity::class, KeywordEntity::class, SpoofProfileEntity::class],
    version = 15,
    exportSchema = true,
)
abstract class ModevDatabase : RoomDatabase() {

    abstract fun cryptoRecordDao(): CryptoRecordDao

    abstract fun keywordLibraryDao(): KeywordLibraryDao

    abstract fun spoofProfileDao(): SpoofProfileDao

    // ---- 治理面 PRAGMA 出口(V45 T1.7 / ST-8·ST-9) ----
    //
    // 唯一出口在本类:仓库不各自拼 PRAGMA(过去无任何治理语句 —— 并发写撞 SQLITE_BUSY
    // 直接抛、clearAll 后文件只增不减、损坏无观测无自愈)。

    /**
     * 执行 PRAGMA。
     *
     * **必须走 `query()` 而不是 `execSQL()`**:Android `SQLiteDatabase.execSQL` 只接受
     * DML/DDL,`PRAGMA …` 会抛 `Queries can be performed using SQLiteDatabase query or
     * rawQuery methods only`(V45 T1.7 首版在此踩实);而 SQLite 的赋值型 PRAGMA 本就
     * 回一行结果,用 query 消费即可。
     */
    private fun SupportSQLiteDatabase.pragma(sql: String): String? =
        query(sql).use { cursor -> if (cursor.moveToFirst()) cursor.getString(0) else null }

    /** 执行返回单列的 PRAGMA;失败(不可读/只读挂载)降级 null,治理面绝不把异常抛上业务路径。 */
    private fun pragma(sql: String): String? = runCatching {
        openHelper.writableDatabase.pragma(sql)
    }.getOrNull()

    /**
     * 首开体检结果(T1.7),三态而非布尔:
     *  - [Healthy] 探针跑通且报 `ok`;
     *  - [Suspect] 探针跑通但 verdict ≠ `ok`(**只有这一态允许删库重建**);
     *  - [Unknown] 探针本身跑不通(平台差异 / PRAGMA 被拒 / 无结果行)。
     *
     * 为什么必须三态:删库是不可逆的数据销毁。若把「探针跑不了」当成「库已坏」,
     * 在一个 query 通道不接受 PRAGMA 的 ROM 上就会每次冷启清空全部记录
     * (V45 设备轮实锤教训:Robolectric 与 FrameworkSQLite 对 PRAGMA 的接受度不同)。
     */
    enum class IntegrityVerdict { Healthy, Suspect, Unknown }

    /** 首开体检(T1.7)。 */
    fun quickCheck(): IntegrityVerdict = verdictOf(
        runCatching {
            openHelper.writableDatabase.query("PRAGMA quick_check").use { c ->
                if (c.moveToFirst()) c.getString(0) else null
            }
        },
    )

    /** 兼容读法:探针跑不通一律按「健康」处理(绝不为误判删数据)。 */
    fun quickCheckOk(): Boolean = quickCheck() != IntegrityVerdict.Suspect

    /** 文件总页数(存储占用诊断用)。 */
    fun pageCount(): Long = pragmaLong("PRAGMA page_count") ?: 0L

    /** freelist 页数(= 已删除但未归还文件的空间)。 */
    fun freePageCount(): Long = pragmaLong("PRAGMA freelist_count") ?: 0L

    /**
     * 存活内容回收(T1.7 / ST-8):**只在便宜时** VACUUM。
     *
     * VACUUM 的耗时代价 = 存活页数(`page_count - freelist_count`),不是文件页数 ——
     * 所以「刚被清空」的大库回收是毫秒级,而「只裁掉几千行」的百万行库必须跳过
     * (宁可暂不收缩,也不给热路径加锁)。
     *
     * 为什么不用 `auto_vacuum=INCREMENTAL`:SQLite 的档位转换本身就得靠一次 VACUUM,
     * 而 Room 的 `onCreate` 回调在建表后、**事务内**(VACUUM 不能在事务里跑);
     * 实测 Room 2.7.2 + FrameworkSQLite 下改档不落地(读回恒 0,V45 T1.7 三轮取证)。
     * 既然回收动作与档位转换用的是同一条 VACUUM,直连 VACUUM + 有界判据就是最短路径,
     * 不留一个永远不生效的配置面(另注:`PRAGMA`/`VACUUM` 都必须走 `query()` 通道,
     * Android 的 `execSQL` 按语句类型白名单直接拒绝)。
     */
    fun reclaimIfCheap(): Boolean {
        val inUse = pageCount() - freePageCount()
        if (inUse > RECLAIM_MAX_INUSE_PAGES) return false
        return runCatching { pragma("VACUUM") }.isSuccess
    }

    /**
     * 大批量删除后的统一收缩出口(T1.7):先按有界判据回收存活页,再收敛 wal。
     * 返回是否做了 VACUUM(wal 检查点恒做)。
     */
    fun shrinkStorage(): Boolean {
        val reclaimed = reclaimIfCheap()
        checkpointWal()
        return reclaimed
    }

    /** WAL 主动检查点(TRUNCATE):抑制 wal 文件无限增长(热进程长跑后的存储占用主因)。 */
    fun checkpointWal(): Boolean = pragma("PRAGMA wal_checkpoint(TRUNCATE)") != null

    /** 数值型 PRAGMA 读取(page_count 等)。 */
    private fun pragmaLong(sql: String): Long? = pragma(sql)?.toLongOrNull()

    companion object {
        /** v1 → v2:纯新增可空列,ALTER TABLE ADD COLUMN 即可,不触碰存量数据。 */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                val table = "crypto_records"
                db.execSQL("ALTER TABLE `$table` ADD COLUMN `saltHex` TEXT")
                db.execSQL("ALTER TABLE `$table` ADD COLUMN `saltText` TEXT")
                db.execSQL("ALTER TABLE `$table` ADD COLUMN `saltLength` INTEGER")
                db.execSQL("ALTER TABLE `$table` ADD COLUMN `saltTruncated` INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE `$table` ADD COLUMN `aadHex` TEXT")
                db.execSQL("ALTER TABLE `$table` ADD COLUMN `aadText` TEXT")
                db.execSQL("ALTER TABLE `$table` ADD COLUMN `aadLength` INTEGER")
                db.execSQL("ALTER TABLE `$table` ADD COLUMN `aadTruncated` INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE `$table` ADD COLUMN `publicKeyHex` TEXT")
                db.execSQL("ALTER TABLE `$table` ADD COLUMN `publicKeyText` TEXT")
                db.execSQL("ALTER TABLE `$table` ADD COLUMN `publicKeyLength` INTEGER")
                db.execSQL("ALTER TABLE `$table` ADD COLUMN `publicKeyTruncated` INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE `$table` ADD COLUMN `paramsJson` TEXT")
                db.execSQL("ALTER TABLE `$table` ADD COLUMN `verifyResult` INTEGER")
                db.execSQL("ALTER TABLE `$table` ADD COLUMN `processName` TEXT")
            }
        }

        /** v2 → v3:新增 seq 列与唯一索引(幂等重放);存量行 seq=NULL 不受唯一约束影响。 */
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                val table = "crypto_records"
                db.execSQL("ALTER TABLE `$table` ADD COLUMN `seq` INTEGER")
                db.execSQL(
                    "CREATE UNIQUE INDEX IF NOT EXISTS `index_crypto_records_packageName_processName_seq` " +
                        "ON `$table` (`packageName`, `processName`, `seq`)"
                )
            }
        }

        /** v3 → v4:新增 recordType 列(记录类型维度);存量行默认 'crypto',与模型默认值一致。 */
        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE `crypto_records` ADD COLUMN `recordType` TEXT NOT NULL DEFAULT 'crypto'"
                )
            }
        }

        /** v4 → v5:新增 timestamp 单列索引(时间倒序热路径,纯增量无数据变更)。 */
        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_crypto_records_timestamp` " +
                        "ON `crypto_records` (`timestamp`)"
                )
            }
        }

        /**
         * v5 → v6:数据库监听(ADR-1)新增 8 个可空列,recordType='db' 行承载;
         * 纯 ALTER TABLE ADD COLUMN,不触碰存量 crypto 行。
         */
        val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                val table = "crypto_records"
                db.execSQL("ALTER TABLE `$table` ADD COLUMN `sql` TEXT")
                db.execSQL("ALTER TABLE `$table` ADD COLUMN `dbPath` TEXT")
                db.execSQL("ALTER TABLE `$table` ADD COLUMN `bindArgsJson` TEXT")
                db.execSQL("ALTER TABLE `$table` ADD COLUMN `rowsJson` TEXT")
                db.execSQL("ALTER TABLE `$table` ADD COLUMN `rowCount` INTEGER")
                db.execSQL("ALTER TABLE `$table` ADD COLUMN `affectedCount` INTEGER")
                db.execSQL("ALTER TABLE `$table` ADD COLUMN `rowId` INTEGER")
                db.execSQL("ALTER TABLE `$table` ADD COLUMN `resultText` TEXT")
            }
        }

        /**
         * v6 → v7:三态视图(V19)7 个字节字段各增 Base64 列;
         * 纯 ALTER TABLE ADD COLUMN,存量行 NULL = 旧记录降级双态(不触碰数据)。
         */
        val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                val table = "crypto_records"
                db.execSQL("ALTER TABLE `$table` ADD COLUMN `keyB64` TEXT")
                db.execSQL("ALTER TABLE `$table` ADD COLUMN `ivB64` TEXT")
                db.execSQL("ALTER TABLE `$table` ADD COLUMN `inputB64` TEXT")
                db.execSQL("ALTER TABLE `$table` ADD COLUMN `outputB64` TEXT")
                db.execSQL("ALTER TABLE `$table` ADD COLUMN `saltB64` TEXT")
                db.execSQL("ALTER TABLE `$table` ADD COLUMN `aadB64` TEXT")
                db.execSQL("ALTER TABLE `$table` ADD COLUMN `publicKeyB64` TEXT")
            }
        }

        /**
         * v7 → v8:多维筛选复合索引(纯 CREATE INDEX,不触碰存量数据)。
         * 索引命名须与实体 [@Index] 生成命名一致,否则运行时 schema 校验抛异常:
         * index_{table}_{col1}_{col2} / index_{table}_{col1}_{col2}_{col3}。
         */
        val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                val table = "crypto_records"
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_${table}_recordType_kind` " +
                        "ON `$table` (`recordType`, `kind`)",
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_${table}_recordType_packageName_timestamp` " +
                        "ON `$table` (`recordType`, `packageName`, `timestamp`)",
                )
            }
        }

        /**
         * v8 → v9:行为监听(V27)新增 10 个可空列,recordType='behavior' 行承载;
         * 纯 ALTER TABLE ADD COLUMN,不触碰存量 crypto/db 行(v5→v6 同先例)。
         */
        val MIGRATION_8_9 = object : Migration(8, 9) {
            override fun migrate(db: SupportSQLiteDatabase) {
                val table = "crypto_records"
                db.execSQL("ALTER TABLE `$table` ADD COLUMN `path` TEXT")
                db.execSQL("ALTER TABLE `$table` ADD COLUMN `destPath` TEXT")
                db.execSQL("ALTER TABLE `$table` ADD COLUMN `command` TEXT")
                db.execSQL("ALTER TABLE `$table` ADD COLUMN `fileSize` INTEGER")
                db.execSQL("ALTER TABLE `$table` ADD COLUMN `contentHex` TEXT")
                db.execSQL("ALTER TABLE `$table` ADD COLUMN `contentB64` TEXT")
                db.execSQL("ALTER TABLE `$table` ADD COLUMN `contentText` TEXT")
                db.execSQL("ALTER TABLE `$table` ADD COLUMN `contentLength` INTEGER")
                db.execSQL("ALTER TABLE `$table` ADD COLUMN `contentTruncated` INTEGER NOT NULL DEFAULT 0")
            }
        }

        /**
         * v9 → v10:偏好监听(V28)新增 1 个可空列 prefKey,recordType='prefs' 行承载;
         * 纯 ALTER TABLE ADD COLUMN,不触碰存量行(v8→v9 同先例)。
         */
        val MIGRATION_9_10 = object : Migration(9, 10) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `crypto_records` ADD COLUMN `prefKey` TEXT")
            }
        }

        /**
         * v10 → v11:网络环境监听(V30)新增 11 个可空列,recordType='network' 行承载;
         * 纯 ALTER TABLE ADD COLUMN,不触碰存量行(v9→v10 同先例)。
         */
        val MIGRATION_10_11 = object : Migration(10, 11) {
            override fun migrate(db: SupportSQLiteDatabase) {
                val table = "crypto_records"
                db.execSQL("ALTER TABLE `$table` ADD COLUMN `url` TEXT")
                db.execSQL("ALTER TABLE `$table` ADD COLUMN `method` TEXT")
                db.execSQL("ALTER TABLE `$table` ADD COLUMN `statusCode` INTEGER")
                db.execSQL("ALTER TABLE `$table` ADD COLUMN `requestHeadersJson` TEXT")
                db.execSQL("ALTER TABLE `$table` ADD COLUMN `responseHeadersJson` TEXT")
                db.execSQL("ALTER TABLE `$table` ADD COLUMN `sslProtocol` TEXT")
                db.execSQL("ALTER TABLE `$table` ADD COLUMN `cipherSuite` TEXT")
                db.execSQL("ALTER TABLE `$table` ADD COLUMN `peerCertificatesJson` TEXT")
                db.execSQL("ALTER TABLE `$table` ADD COLUMN `proxyHost` TEXT")
                db.execSQL("ALTER TABLE `$table` ADD COLUMN `proxyPort` INTEGER")
                db.execSQL("ALTER TABLE `$table` ADD COLUMN `vpnActive` INTEGER")
            }
        }

        /**
         * v11 → v12:V33 三大类监听新增 27 个可空列;
         * 纯 ALTER TABLE ADD COLUMN,不触碰存量行(v10→v11 同先例)。
         */
        val MIGRATION_11_12 = object : Migration(11, 12) {
            override fun migrate(db: SupportSQLiteDatabase) {
                val table = "crypto_records"
                // UI 交互监听列(11)
                db.execSQL("ALTER TABLE `$table` ADD COLUMN `dialogTitle` TEXT")
                db.execSQL("ALTER TABLE `$table` ADD COLUMN `dialogMessage` TEXT")
                db.execSQL("ALTER TABLE `$table` ADD COLUMN `dialogKeywords` TEXT")
                db.execSQL("ALTER TABLE `$table` ADD COLUMN `dialogIntercepted` INTEGER")
                db.execSQL("ALTER TABLE `$table` ADD COLUMN `widgetText` TEXT")
                db.execSQL("ALTER TABLE `$table` ADD COLUMN `widgetClassName` TEXT")
                db.execSQL("ALTER TABLE `$table` ADD COLUMN `activityName` TEXT")
                db.execSQL("ALTER TABLE `$table` ADD COLUMN `intentAction` TEXT")
                db.execSQL("ALTER TABLE `$table` ADD COLUMN `intentData` TEXT")
                db.execSQL("ALTER TABLE `$table` ADD COLUMN `intentExtrasJson` TEXT")
                db.execSQL("ALTER TABLE `$table` ADD COLUMN `intentFlags` INTEGER")
                // 系统信息监听列(10)
                db.execSQL("ALTER TABLE `$table` ADD COLUMN `logcatTag` TEXT")
                db.execSQL("ALTER TABLE `$table` ADD COLUMN `logcatLevel` TEXT")
                db.execSQL("ALTER TABLE `$table` ADD COLUMN `logcatMessage` TEXT")
                db.execSQL("ALTER TABLE `$table` ADD COLUMN `appVersion` TEXT")
                db.execSQL("ALTER TABLE `$table` ADD COLUMN `appVersionCode` INTEGER")
                db.execSQL("ALTER TABLE `$table` ADD COLUMN `appMinSdk` INTEGER")
                db.execSQL("ALTER TABLE `$table` ADD COLUMN `appTargetSdk` INTEGER")
                db.execSQL("ALTER TABLE `$table` ADD COLUMN `appPermissions` TEXT")
                db.execSQL("ALTER TABLE `$table` ADD COLUMN `signatureHash` TEXT")
                db.execSQL("ALTER TABLE `$table` ADD COLUMN `signatureBase64` TEXT")
                // 进程控制列(6)
                db.execSQL("ALTER TABLE `$table` ADD COLUMN `exitCode` INTEGER")
                db.execSQL("ALTER TABLE `$table` ADD COLUMN `processPid` INTEGER")
                db.execSQL("ALTER TABLE `$table` ADD COLUMN `crashType` TEXT")
                db.execSQL("ALTER TABLE `$table` ADD COLUMN `crashMessage` TEXT")
                db.execSQL("ALTER TABLE `$table` ADD COLUMN `crashBlocked` INTEGER")
                db.execSQL("ALTER TABLE `$table` ADD COLUMN `screenRecordBlocked` INTEGER")
            }
        }

        /**
         * v12 → v13:V34 审计增强新增 interventionAction 列(干预动作标记);
         * 覆盖进程控制/设备环境/UI交互三类记录;纯 ALTER TABLE ADD COLUMN,不触碰存量行。
         */
        val MIGRATION_12_13 = object : Migration(12, 13) {
            override fun migrate(db: SupportSQLiteDatabase) {
                val table = "crypto_records"
                db.execSQL("ALTER TABLE `$table` ADD COLUMN `interventionAction` TEXT")
            }
        }

        /**
         * v13 → v14:V37 T2.1 新增关键词库表(建表 SQL 与 Room 生成 schema 逐字一致,
         * 否则运行时校验抛异常);纯增量,不触碰存量记录。
         */
        val MIGRATION_13_14 = object : Migration(13, 14) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `keyword_library` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`text` TEXT NOT NULL, " +
                        "`matchMode` TEXT NOT NULL, " +
                        "`enabled` INTEGER NOT NULL, " +
                        "`createdAt` INTEGER NOT NULL)"
                )
                db.execSQL(
                    "CREATE UNIQUE INDEX IF NOT EXISTS `index_keyword_library_text` " +
                        "ON `keyword_library` (`text`)"
                )
            }
        }

        /**
         * v14 → v15:V47 T2 设备画像重建。两件事同版本一次落地:
         *  1. 新增 `spoof_profile` 表(画像库:41 字段画像正文 JSON + 内置档账本列 +
         *     删除墓碑),纯 CREATE TABLE + INDEX,不触碰存量记录;
         *  2. `crypto_records` 新增可空列 `maskedValue`(干预后的值),把设备环境记录
         *     升级为「原值 → 伪装值」双侧 oracle —— 此前 maskedValue 只存在于内存模型、
         *     读回恒 null(AGENTS 实锤 DFC-03),使伪装审计无第二证据源。
         *     纯 ALTER TABLE ADD COLUMN,存量行该列为 NULL(语义 = 未记录,非空值)。
         */
        val MIGRATION_14_15 = object : Migration(14, 15) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `spoof_profile` (" +
                        "`id` TEXT NOT NULL, " +
                        "`name` TEXT NOT NULL, " +
                        "`summary` TEXT NOT NULL, " +
                        "`profileJson` TEXT NOT NULL, " +
                        "`builtinSeedId` TEXT, " +
                        "`builtinVersion` INTEGER, " +
                        "`builtinFingerprint` TEXT, " +
                        "`managed` INTEGER NOT NULL, " +
                        "`deleted` INTEGER NOT NULL, " +
                        "`createTime` INTEGER NOT NULL, " +
                        "`updateTime` INTEGER NOT NULL, " +
                        "PRIMARY KEY(`id`))"
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_spoof_profile_builtinSeedId` " +
                        "ON `spoof_profile` (`builtinSeedId`)"
                )
                db.execSQL(
                    "ALTER TABLE `${CryptoRecordEntity.TABLE_NAME}` " +
                        "ADD COLUMN `maskedValue` TEXT"
                )
            }
        }

        /**
         * 迁移清单**唯一事实源**(V47 T2 收敛):`builder()` 与全部迁移测试一律引用本数组,
         * 不再各自枚举 `MIGRATION_x_y`。
         *
         * 收敛动因:此前生产侧三处(version / 定义 / addMigrations)由
         * [com.lioisme.modev.architecture.DbMigrationParityTest] 盯住,但测试侧另有
         * 七处手写清单在门禁视野之外 —— 每次升版本都要挨个补一行,漏一处即
         * 「A migration from N to N+1 was required but not found」的假失败。
         * 现在升版本只需:定义迁移 → 加进本数组 → 同步 `version`,测试零改动
         * (禁就地枚举由同一门禁的 `migrationListHasSingleSource` 强制)。
         */
        val ALL_MIGRATIONS: Array<Migration> = arrayOf(
            MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5,
            MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8, MIGRATION_8_9,
            MIGRATION_9_10, MIGRATION_10_11, MIGRATION_11_12,
            MIGRATION_12_13, MIGRATION_13_14, MIGRATION_14_15,
        )

        /** 数据库文件名(存储占用统计/测试同源)。 */
        const val NAME = "modev.db"
        /**
         * 体检判定表(T1.7,纯函数 = 删库这一不可逆动作的唯一裁决点):
         * 只有「探针跑通且读到非 `ok` 结论」才是 Suspect;探针自身失败或无结果行
         * 一律 Unknown(调用方必须放行,不得删数据)。
         */
        internal fun verdictOf(result: Result<String?>): IntegrityVerdict = when {
            result.isFailure -> IntegrityVerdict.Unknown
            result.getOrNull() == null -> IntegrityVerdict.Unknown
            result.getOrNull() == "ok" -> IntegrityVerdict.Healthy
            else -> IntegrityVerdict.Suspect
        }

        /**
         * 存活内容回收上限(T1.7):4KiB × 2000 ≈ 8MiB 存活内容 —— 这个量级的 VACUUM
         * 是毫秒级;超过就不强收(见 [ModevDatabase.reclaimIfCheap])。
         */
        private const val RECLAIM_MAX_INUSE_PAGES = 2_000L

        /**
         * PRAGMA 治理唯一出口(V45 T1.7 / ST-8):`onOpen` 每次打开定档 ——
         * `busy_timeout` 让注入侧批量写与 UI 分页读并发时由 SQLite 自行退避重试
         * (默认 0 等待 = 直接抛 `SQLITE_BUSY`,表现为「写入失败」计数上涨);
         * `wal_autocheckpoint` 收敛到 200 页,防单次检查点在大 wal 上造成长停顿。
         *
         * 无 `onCreate` 覆盖:收缩策略是「有界直连 VACUUM」而非 `auto_vacuum` 档位
         * (档位转换在本栈上不可靠,原因见 [ModevDatabase.reclaimIfCheap] 的 KDoc)。
         */
        internal val GOVERNANCE = object : RoomDatabase.Callback() {
            override fun onOpen(db: SupportSQLiteDatabase) {
                db.applyPragma("PRAGMA busy_timeout = 5000")
                db.applyPragma("PRAGMA wal_autocheckpoint = 200")
            }
        }

        /** 见 [ModevDatabase.pragma]:PRAGMA 一律 query 消费,禁 `execSQL`(Android 侧直接抛)。 */
        private fun SupportSQLiteDatabase.applyPragma(sql: String) {
            runCatching { query(sql).use { it.moveToFirst() } }
                .onFailure { Log.w(TAG, "pragma $sql failed: $it") }
        }

        @Volatile
        private var instance: ModevDatabase? = null

        /**
         * 损坏自愈标记(V45 T1.7):写路径抛出损坏类异常时由 [noteSqliteError] 置位,
         * 下次 [get] 直接重建文件(不等 quick_check)。运行中不换实例 ——
         * 仓库/DAO 持有的是构造期快照,进程内换实例会让旧引用打到已关闭的库上
         * (V38「注入早于 Application」同型教训:定档必须在路径可见时做)。
         */
        @Volatile
        private var recoveryPending = false

        /** 观测面:是否已检测到损坏并排期重建(Diagnostics/MCP diagnostics 消费)。 */
        fun isRecoveryPending(): Boolean = recoveryPending

        /** 测试/复位口:清除排期标记。 */
        internal fun clearRecoveryPendingForTest() {
            recoveryPending = false
        }

        /**
         * 测试口:释放单例(下次 [get] 重新建库 + 重跑首开体检)。
         * 生产路径永不调用 —— 仓库/DAO 持的是构造期快照,进程内换实例等于给旧引用一把
         * 已关闭的库(V38 同源教训),因此「运行中自愈」被刻意设计成「下次打开自愈」。
         */
        internal fun resetInstanceForTest() {
            synchronized(this) {
                runCatching { instance?.close() }
                instance = null
            }
        }

        fun get(context: Context): ModevDatabase =
            instance ?: synchronized(this) {
                instance ?: open(context.applicationContext).also { instance = it }
            }

        /**
         * 建库 + 首开体检(T1.7):`recoveryPending` 或 `quick_check` 不过 → 删文件重建一次。
         *
         * 为什么只重建一次:损坏多因于进程被强杀/磁盘满,首开即修好;若重建后仍不可读,
         * 说明是环境级故障(权限/只读挂载),反复删库只会静默丢数据,故第二次失败直接放行,
         * 让 Room 的异常把问题顶到崩溃日志与诊断面上(可归因 > 自动掩盖)。
         */
        private fun open(appContext: Context): ModevDatabase {
            if (recoveryPending) rebuild(appContext)
            val first = builder(appContext).build()
            return when (first.quickCheck()) {
                IntegrityVerdict.Suspect -> {
                    Log.w(TAG, "quick_check reports corruption; rebuilding database once")
                    first.close()
                    rebuild(appContext)
                    builder(appContext).build()
                }
                // Unknown = 探针在本机跑不通:绝不据此删数据(只放行,异常留给业务路径暴露)
                IntegrityVerdict.Unknown -> {
                    Log.i(TAG, "quick_check unavailable on this platform; skipping auto-recovery")
                    first
                }
                IntegrityVerdict.Healthy -> first
            }
        }

        /**
         * 损坏观测接线(T1.7 / ST-9)。
         *
         * **Room 2.7.2 无 `DatabaseErrorListener`**(该 API 不在本版本 aar 内 ——
         * 已核对 classes.jar,勿再按新版文档写接线)。SQLite 的 corruption 只能从
         * 抛出的异常里分类得到,故观测面 = 业务路径捕获异常时交进来判定:
         * 命中损坏特征 → 置 [recoveryPending](下次 [get] 重建)+ 落一次 `Log.ERROR`。
         * 返回是否判定为损坏,调用方可据此把该次失败单独计数(不再混进 writeFailures)。
         */
        fun noteSqliteError(error: Throwable): Boolean {
            if (!isCorruptionMessage(error.message)) return false
            val first = !recoveryPending
            recoveryPending = true
            if (first) {
                Log.e(TAG, "database corruption detected; rebuild scheduled for next open: ${error.message}")
            }
            return true
        }

        /** SQLite 损坏特征串(3.x 的三种措辞;大小写不敏感)。 */
        internal fun isCorruptionMessage(message: String?): Boolean {
            val m = message?.lowercase() ?: return false
            return "database disk image is malformed" in m ||
                "file is not a database" in m ||
                "sqlite_corrupt" in m ||
                "database file is encrypted" in m
        }

        private const val TAG = "ModevDb"

        private fun rebuild(appContext: Context) {
            appContext.deleteDatabase(NAME)
            // 一并清掉旁路文件(Room 删库不保证 wal/shm 同步消失)
            listOf("$NAME-wal", "$NAME-shm").forEach { appContext.deleteFile(it) }
            recoveryPending = false
        }

        private fun builder(appContext: Context): RoomDatabase.Builder<ModevDatabase> =
            Room.databaseBuilder(appContext, ModevDatabase::class.java, NAME)
                // V23 T6.1:显式 WAL(写并发读、批量落库 fsync 摊薄;
                // AUTOMATIC 在 API 16+ 实际同为 WAL,显式化表达意图并防 OEM 差异)
                .setJournalMode(RoomDatabase.JournalMode.WRITE_AHEAD_LOGGING)
                .addMigrations(*ALL_MIGRATIONS)
                .addCallback(GOVERNANCE)
                .fallbackToDestructiveMigration(false)
    }
}
