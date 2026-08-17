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
 */
@Database(
    entities = [CryptoRecordEntity::class],
    version = 3,
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

        @Volatile
        private var instance: ModevDatabase? = null

        fun get(context: Context): ModevDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    ModevDatabase::class.java,
                    "modev.db",
                )
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
                    .fallbackToDestructiveMigration(false)
                    .build()
                    .also { instance = it }
            }
    }
}