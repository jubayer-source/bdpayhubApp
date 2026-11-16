package com.example.core.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [PaymentEventEntity::class, DiagnosticLogEntity::class],
    version = 1,
    exportSchema = false
)
abstract class BDPayHubDatabase : RoomDatabase() {
    abstract fun paymentEventDao(): PaymentEventDao

    companion object {
        @Volatile
        private var INSTANCE: BDPayHubDatabase? = null

        fun getInstance(context: Context): BDPayHubDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    BDPayHubDatabase::class.java,
                    "bdpayhub_events.db"
                )
                    .fallbackToDestructiveMigration(false)
                    .build()
                    .also { INSTANCE = it }
            }
        }

        fun createInMemory(context: Context): BDPayHubDatabase {
            return Room.inMemoryDatabaseBuilder(
                context.applicationContext,
                BDPayHubDatabase::class.java
            )
                .allowMainThreadQueries()
                .build()
        }
    }
}
