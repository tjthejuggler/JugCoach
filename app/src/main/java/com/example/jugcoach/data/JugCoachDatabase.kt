package com.example.jugcoach.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.example.jugcoach.data.converter.DateConverter
import com.example.jugcoach.data.converter.ListConverter
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
    version = 2,
    exportSchema = true
)
@TypeConverters(DateConverter::class, ListConverter::class)
abstract class JugCoachDatabase : RoomDatabase() {
    abstract fun patternDao(): PatternDao
    abstract fun sessionDao(): SessionDao
    abstract fun noteDao(): NoteDao
    abstract fun settingsDao(): SettingsDao
    abstract fun coachDao(): CoachDao

    companion object {
        @Volatile
        private var INSTANCE: JugCoachDatabase? = null

        fun getDatabase(context: Context): JugCoachDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    JugCoachDatabase::class.java,
                    "jugcoach_database"
                )
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
