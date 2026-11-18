package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        ServerProfileEntity::class,
        AgentSessionEntity::class,
        SessionMessageEntity::class,
        FileDiffEntity::class,
        TerminalLogEntity::class,
        CopilotMessageEntity::class
    ],
    version = 2,
    exportSchema = false
)
abstract class AntigravityDatabase : RoomDatabase() {
    abstract fun dao(): AntigravityDao

    companion object {
        @Volatile
        private var INSTANCE: AntigravityDatabase? = null

        fun getInstance(context: Context): AntigravityDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AntigravityDatabase::class.java,
                    "antigravity_pulse.db"
                )
                    .fallbackToDestructiveMigration(dropAllTables = true)
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }
}
