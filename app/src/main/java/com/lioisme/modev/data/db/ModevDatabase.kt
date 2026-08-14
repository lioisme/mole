package com.lioisme.modev.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

/**
 * 宿主侧数据库。单进程单例,避免多实例竞争。
 */
@Database(
    entities = [CryptoRecordEntity::class],
    version = 1,
    exportSchema = true,
)
abstract class ModevDatabase : RoomDatabase() {

    abstract fun cryptoRecordDao(): CryptoRecordDao

    companion object {
        @Volatile
        private var instance: ModevDatabase? = null

        fun get(context: Context): ModevDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    ModevDatabase::class.java,
                    "modev.db",
                )
                    .fallbackToDestructiveMigration(false)
                    .build()
                    .also { instance = it }
            }
    }
}