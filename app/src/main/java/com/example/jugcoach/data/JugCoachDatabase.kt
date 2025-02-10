package com.example.jugcoach.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.example.jugcoach.data.converter.DateConverter
import com.example.jugcoach.data.converter.ListConverter
import com.example.jugcoach.data.converter.RunListConverter
import com.example.jugcoach.data.dao.*
import com.example.jugcoach.data.entity.*

@Database(
    entities = [
        Pattern::class,
        Session::class,
        Note::class,
        Settings::class,
        Coach::class
    ],
    version = 4,
    exportSchema = true
)
@TypeConverters(DateConverter::class, ListConverter::class, RunListConverter::class)
abstract class JugCoachDatabase : RoomDatabase() {
    abstract fun patternDao(): PatternDao
    abstract fun sessionDao(): SessionDao
    abstract fun noteDao(): NoteDao
    abstract fun settingsDao(): SettingsDao
    abstract fun coachDao(): CoachDao

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

        fun getDatabase(context: Context): JugCoachDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    JugCoachDatabase::class.java,
                    "jugcoach_database"
                )
                    .fallbackToDestructiveMigration()
                    .addMigrations(MIGRATION_2_3, MIGRATION_3_4)
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
