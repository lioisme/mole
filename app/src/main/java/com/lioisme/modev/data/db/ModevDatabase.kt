package com.lioisme.modev.data.db

import android.content.Context
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
 */
@Database(
    entities = [CryptoRecordEntity::class],
    version = 10,
    exportSchema = true,
)
abstract class ModevDatabase : RoomDatabase() {

    abstract fun cryptoRecordDao(): CryptoRecordDao

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

        @Volatile
        private var instance: ModevDatabase? = null

        fun get(context: Context): ModevDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    ModevDatabase::class.java,
                    NAME,
                )
                    // V23 T6.1:显式 WAL(写并发读、批量落库 fsync 摊薄;
                    // AUTOMATIC 在 API 16+ 实际同为 WAL,显式化表达意图并防 OEM 差异)
                    .setJournalMode(RoomDatabase.JournalMode.WRITE_AHEAD_LOGGING)
                    .addMigrations(
                        MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5,
                        MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8, MIGRATION_8_9,
                        MIGRATION_9_10,
                    )
                    .fallbackToDestructiveMigration(false)
                    .build()
                    .also { instance = it }
            }

        /** 数据库文件名(存储占用统计/测试同源)。 */
        const val NAME = "modev.db"
    }
}