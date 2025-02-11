package com.example.jugcoach.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.example.jugcoach.data.converter.DateConverter
import com.example.jugcoach.data.converter.ListConverter
import com.example.jugcoach.data.converter.ProposalStatusConverter
import com.example.jugcoach.data.converter.RunListConverter
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
        CoachProposal::class
    ],
    version = 9,
    exportSchema = true
)
@TypeConverters(DateConverter::class, ListConverter::class, RunListConverter::class, ProposalStatusConverter::class)
abstract class JugCoachDatabase : RoomDatabase() {
    abstract fun patternDao(): PatternDao
    abstract fun sessionDao(): SessionDao
    abstract fun noteDao(): NoteDao
    abstract fun settingsDao(): SettingsDao
    abstract fun coachDao(): CoachDao
    abstract fun conversationDao(): ConversationDao
    abstract fun chatMessageDao(): ChatMessageDao
    abstract fun coachProposalDao(): CoachProposalDao

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
                        MIGRATION_8_9
                    )
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
