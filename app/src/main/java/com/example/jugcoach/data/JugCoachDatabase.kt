package com.example.jugcoach.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.example.jugcoach.data.converter.*
import com.example.jugcoach.data.dao.*
import com.example.jugcoach.data.entity.*

@Database(
    entities = [
        Pattern::class,
        Session::class,
        Note::class,
        Settings::class,
        Coach::class,
        Conversation::class,
        ChatMessage::class,
        CoachProposal::class,
        HistoryEntry::class
    ],
    version = 16,
    exportSchema = true
)
@TypeConverters(
    DateConverter::class,
    ListConverter::class,
    RunListConverter::class,
    ProposalStatusConverter::class,
    MessageTypeConverter::class
)
abstract class JugCoachDatabase : RoomDatabase() {
    abstract fun patternDao(): PatternDao
    abstract fun sessionDao(): SessionDao
    abstract fun noteDao(): NoteDao
    abstract fun settingsDao(): SettingsDao
    abstract fun coachDao(): CoachDao
    abstract fun conversationDao(): ConversationDao
    abstract fun chatMessageDao(): ChatMessageDao
    abstract fun coachProposalDao(): CoachProposalDao
    abstract fun historyEntryDao(): HistoryEntryDao

    companion object {
        @Volatile
        private var INSTANCE: JugCoachDatabase? = null

        private val MIGRATION_2_3 = object : androidx.room.migration.Migration(2, 3) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                // Add history_runs column with default empty JSON array
                db.execSQL("""
                    ALTER TABLE patterns ADD COLUMN history_runs TEXT NOT NULL DEFAULT '[]'
                """)
            }
        }

        private val MIGRATION_3_4 = object : androidx.room.migration.Migration(3, 4) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                // Add coachId columns
                db.execSQL("ALTER TABLE patterns ADD COLUMN coachId INTEGER DEFAULT NULL REFERENCES coaches(id) ON DELETE SET NULL")
                db.execSQL("CREATE INDEX index_patterns_coachId ON patterns(coachId)")

                db.execSQL("ALTER TABLE sessions ADD COLUMN coachId INTEGER NOT NULL DEFAULT 1 REFERENCES coaches(id) ON DELETE CASCADE")
                db.execSQL("CREATE INDEX index_sessions_coachId ON sessions(coachId)")

                db.execSQL("ALTER TABLE notes ADD COLUMN coachId INTEGER NOT NULL DEFAULT 1 REFERENCES coaches(id) ON DELETE CASCADE")
                db.execSQL("CREATE INDEX index_notes_coachId ON notes(coachId)")

                // Create a default head coach if none exists
                db.execSQL("""
                    INSERT OR IGNORE INTO coaches (id, name, apiKeyName, isHeadCoach, createdAt)
                    VALUES (1, 'Head Coach', 'default', 1, ${System.currentTimeMillis()})
                """)
            }
        }

        private val MIGRATION_4_5 = object : androidx.room.migration.Migration(4, 5) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                // Create temporary table
                db.execSQL("""
                    CREATE TABLE coaches_temp (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        name TEXT NOT NULL,
                        apiKeyName TEXT NOT NULL,
                        description TEXT,
                        specialties TEXT,
                        isHeadCoach INTEGER NOT NULL,
                        createdAt INTEGER NOT NULL
                    )
                """)

                // Copy data from old table to new table
                db.execSQL("""
                    INSERT INTO coaches_temp (id, name, apiKeyName, description, specialties, isHeadCoach, createdAt)
                    SELECT id, name, apiKeyName, description, specialties, isHeadCoach, createdAt
                    FROM coaches
                """)

                // Drop old table
                db.execSQL("DROP TABLE coaches")

                // Rename temp table to original name
                db.execSQL("ALTER TABLE coaches_temp RENAME TO coaches")

                // Recreate index
                db.execSQL("CREATE UNIQUE INDEX index_coaches_name ON coaches(name)")
            }
        }

        private val MIGRATION_5_6 = object : androidx.room.migration.Migration(5, 6) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                // Add systemPrompt column
                db.execSQL("ALTER TABLE coaches ADD COLUMN systemPrompt TEXT DEFAULT NULL")
            }
        }

        private val MIGRATION_6_7 = object : androidx.room.migration.Migration(6, 7) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                // Create conversations table
                db.execSQL("""
                    CREATE TABLE conversations (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        coachId INTEGER NOT NULL,
                        title TEXT,
                        createdAt INTEGER NOT NULL,
                        isFavorite INTEGER NOT NULL DEFAULT 0,
                        lastMessageAt INTEGER NOT NULL,
                        FOREIGN KEY(coachId) REFERENCES coaches(id) ON DELETE CASCADE
                    )
                """)
                db.execSQL("CREATE INDEX index_conversations_coachId ON conversations(coachId)")

                // Create chat_messages table
                db.execSQL("""
                    CREATE TABLE chat_messages (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        conversationId INTEGER NOT NULL,
                        text TEXT NOT NULL,
                        isFromUser INTEGER NOT NULL,
                        timestamp INTEGER NOT NULL,
                        isError INTEGER NOT NULL DEFAULT 0,
                        FOREIGN KEY(conversationId) REFERENCES conversations(id) ON DELETE CASCADE
                    )
                """)
                db.execSQL("CREATE INDEX index_chat_messages_conversationId ON chat_messages(conversationId)")
            }
        }

        private val MIGRATION_7_8 = object : androidx.room.migration.Migration(7, 8) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                // Add new indices for chat_messages
                db.execSQL("CREATE INDEX index_chat_messages_timestamp ON chat_messages(timestamp)")
                db.execSQL("CREATE INDEX index_chat_messages_conversation_timestamp ON chat_messages(conversationId, timestamp)")
            }
        }

        private val MIGRATION_8_9 = object : androidx.room.migration.Migration(8, 9) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                // Create coach_proposals table
                db.execSQL("""
                    CREATE TABLE coach_proposals (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        patternId TEXT NOT NULL,
                        coachId INTEGER NOT NULL,
                        proposedChanges TEXT NOT NULL,
                        status TEXT NOT NULL,
                        createdAt INTEGER NOT NULL,
                        notes TEXT,
                        FOREIGN KEY(patternId) REFERENCES patterns(id) ON DELETE CASCADE,
                        FOREIGN KEY(coachId) REFERENCES coaches(id) ON DELETE CASCADE
                    )
                """)
                
                // Create indices
                db.execSQL("CREATE INDEX index_coach_proposals_patternId ON coach_proposals(patternId)")
                db.execSQL("CREATE INDEX index_coach_proposals_coachId ON coach_proposals(coachId)")
                db.execSQL("CREATE INDEX index_coach_proposals_status ON coach_proposals(status)")
            }
        }

        private val MIGRATION_9_10 = object : androidx.room.migration.Migration(9, 10) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                // Add messageType column with default value TALKING
                db.execSQL("ALTER TABLE chat_messages ADD COLUMN messageType TEXT NOT NULL DEFAULT 'TALKING'")
            }
        }

        private val MIGRATION_10_11 = object : androidx.room.migration.Migration(10, 11) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                // Add isInternal column with default value false
                db.execSQL("ALTER TABLE chat_messages ADD COLUMN isInternal INTEGER NOT NULL DEFAULT 0")
            }
        }

        private val MIGRATION_11_12 = object : androidx.room.migration.Migration(11, 12) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                // Add model and apiKeyName columns
                db.execSQL("ALTER TABLE chat_messages ADD COLUMN model TEXT")
                db.execSQL("ALTER TABLE chat_messages ADD COLUMN apiKeyName TEXT")
            }
        }

        private val MIGRATION_12_13 = object : androidx.room.migration.Migration(12, 13) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                // Add catchesPerMinute column to patterns table
                db.execSQL("ALTER TABLE patterns ADD COLUMN catchesPerMinute REAL DEFAULT NULL")
            }
        }

        private val MIGRATION_13_14 = object : androidx.room.migration.Migration(13, 14) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                // Add video time columns to patterns table
                db.execSQL("ALTER TABLE patterns ADD COLUMN videoStartTime INTEGER DEFAULT NULL")
                db.execSQL("ALTER TABLE patterns ADD COLUMN videoEndTime INTEGER DEFAULT NULL")
            }
        }

        private val MIGRATION_14_15 = object : androidx.room.migration.Migration(14, 15) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                // Get all patterns with their run histories
                val cursor = db.query("SELECT id, history_runs, record_catches, record_date FROM patterns")
                
                while (cursor.moveToNext()) {
                    val id = cursor.getString(cursor.getColumnIndexOrThrow("id"))
                    val runsJson = cursor.getString(cursor.getColumnIndexOrThrow("history_runs"))
                    
                    // Parse run history JSON to find highest catch count
                    try {
                        val runs = com.google.gson.JsonParser.parseString(runsJson).asJsonArray
                        var maxCatches: Int? = null
                        var maxCatchDate: Long? = null
                        
                        for (run in runs) {
                            val runObj = run.asJsonObject
                            if (runObj.has("catches") && !runObj.get("catches").isJsonNull) {
                                val catches = runObj.get("catches").asInt
                                val date = runObj.get("date").asLong
                                
                                if (maxCatches == null || catches > maxCatches) {
                                    maxCatches = catches
                                    maxCatchDate = date
                                }
                            }
                        }
                        
                        // Update record if needed
                        if (maxCatches != null) {
                            val currentRecord = if (!cursor.isNull(cursor.getColumnIndexOrThrow("record_catches"))) {
                                cursor.getInt(cursor.getColumnIndexOrThrow("record_catches"))
                            } else null
                            
                            if (currentRecord == null || maxCatches > currentRecord) {
                                db.execSQL("""
                                    UPDATE patterns
                                    SET record_catches = ?, record_date = ?
                                    WHERE id = ?
                                """, arrayOf(maxCatches, maxCatchDate, id))
                            }
                        }
                    } catch (e: Exception) {
                        android.util.Log.e("Migration14_15", "Error processing pattern $id: ${e.message}")
                    }
                }
                cursor.close()
            }
        }

        private val MIGRATION_15_16 = object : androidx.room.migration.Migration(15, 16) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                // Create the history_entries table
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `history_entries` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `type` TEXT NOT NULL,
                        `timestamp` INTEGER NOT NULL,
                        `description` TEXT NOT NULL,
                        `relatedPatternId` TEXT,
                        `relatedCoachId` INTEGER,
                        `isFromUser` INTEGER NOT NULL DEFAULT 1,
                        FOREIGN KEY(`relatedPatternId`) REFERENCES `patterns`(`id`) ON UPDATE NO ACTION ON DELETE SET NULL,
                        FOREIGN KEY(`relatedCoachId`) REFERENCES `coaches`(`id`) ON UPDATE NO ACTION ON DELETE SET NULL
                    )
                """)
                
                // Create indices for faster lookups
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_history_entries_relatedPatternId` ON `history_entries` (`relatedPatternId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_history_entries_relatedCoachId` ON `history_entries` (`relatedCoachId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_history_entries_timestamp` ON `history_entries` (`timestamp`)")
            }
        }

        fun getDatabase(context: Context): JugCoachDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    JugCoachDatabase::class.java,
                    "jugcoach_database"
                )
                    .fallbackToDestructiveMigration()
                    .addMigrations(
                        MIGRATION_2_3,
                        MIGRATION_3_4,
                        MIGRATION_4_5,
                        MIGRATION_5_6,
                        MIGRATION_6_7,
                        MIGRATION_7_8,
                        MIGRATION_8_9,
                        MIGRATION_9_10,
                        MIGRATION_10_11,
                        MIGRATION_11_12,
                        MIGRATION_12_13,
                        MIGRATION_13_14,
                        MIGRATION_14_15,
                        MIGRATION_15_16
                    )
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
