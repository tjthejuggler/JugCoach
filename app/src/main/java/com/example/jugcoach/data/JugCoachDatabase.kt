package com.example.jugcoach.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.example.jugcoach.data.converter.DateConverter
import com.example.jugcoach.data.converter.ListConverter
import com.example.jugcoach.data.dao.NoteDao
import com.example.jugcoach.data.dao.PatternDao
import com.example.jugcoach.data.dao.SessionDao
import com.example.jugcoach.data.dao.SettingsDao
import com.example.jugcoach.data.entity.Note
import com.example.jugcoach.data.entity.Pattern
import com.example.jugcoach.data.entity.Session
import com.example.jugcoach.data.entity.Settings

@Database(
    entities = [
        Pattern::class,
        Session::class,
        Note::class,
        Settings::class
    ],
    version = 1,
    exportSchema = true
)
@TypeConverters(DateConverter::class, ListConverter::class)
abstract class JugCoachDatabase : RoomDatabase() {
    abstract fun patternDao(): PatternDao
    abstract fun sessionDao(): SessionDao
    abstract fun noteDao(): NoteDao
    abstract fun settingsDao(): SettingsDao

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
                    .fallbackToDestructiveMigration() // For development only, remove in production
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
