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
 */
@Database(
    entities = [CryptoRecordEntity::class],
    version = 6,
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

        @Volatile
        private var instance: ModevDatabase? = null

        fun get(context: Context): ModevDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    ModevDatabase::class.java,
                    "modev.db",
                )
                    .addMigrations(
                        MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5,
                        MIGRATION_5_6,
                    )
                    .fallbackToDestructiveMigration(false)
                    .build()
                    .also { instance = it }
            }
    }
}