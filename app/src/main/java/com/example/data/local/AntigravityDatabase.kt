package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [ServerProfileEntity::class],
    version = 4,
    exportSchema = false
)
abstract class AntigravityDatabase : RoomDatabase() {
    abstract fun dao(): AntigravityDao

    companion object {
        @Volatile
        private var INSTANCE: AntigravityDatabase? = null

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `ai_studio_presets` (
                        `id` TEXT NOT NULL,
                        `title` TEXT NOT NULL,
                        `systemInstruction` TEXT NOT NULL,
                        `starterPrompt` TEXT NOT NULL,
                        `modelId` TEXT NOT NULL,
                        `temperature` REAL NOT NULL,
                        `updatedAtEpoch` INTEGER NOT NULL,
                        PRIMARY KEY(`id`)
                    )
                    """.trimIndent()
                )
            }
        }

        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Non-destructive cleanup: drop obsolete legacy tables while preserving user server_profiles
                db.execSQL("DROP TABLE IF EXISTS `agent_sessions`")
                db.execSQL("DROP TABLE IF EXISTS `session_messages`")
                db.execSQL("DROP TABLE IF EXISTS `file_diffs`")
                db.execSQL("DROP TABLE IF EXISTS `terminal_logs`")
                db.execSQL("DROP TABLE IF EXISTS `copilot_messages`")
                db.execSQL("DROP TABLE IF EXISTS `ai_studio_presets`")
            }
        }

        fun getInstance(context: Context): AntigravityDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AntigravityDatabase::class.java,
                    "antigravity_pulse.db"
                )
                    .addMigrations(MIGRATION_2_3, MIGRATION_3_4)
                    .fallbackToDestructiveMigration(dropAllTables = true)
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }
}
