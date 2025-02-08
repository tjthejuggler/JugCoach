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
    version = 3,
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
            override fun migrate(database: androidx.sqlite.db.SupportSQLiteDatabase) {
                // Add history_runs column with default empty JSON array
                database.execSQL("""
                    ALTER TABLE patterns ADD COLUMN history_runs TEXT NOT NULL DEFAULT '[]'
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
                    .addMigrations(MIGRATION_2_3)
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
