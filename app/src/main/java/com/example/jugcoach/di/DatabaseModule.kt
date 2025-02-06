package com.example.jugcoach.di

import android.content.Context
import com.example.jugcoach.data.JugCoachDatabase
import com.example.jugcoach.data.dao.NoteDao
import com.example.jugcoach.data.dao.PatternDao
import com.example.jugcoach.data.dao.SessionDao
import com.example.jugcoach.data.dao.SettingsDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): JugCoachDatabase {
        return JugCoachDatabase.getDatabase(context)
    }

    @Provides
    fun providePatternDao(database: JugCoachDatabase): PatternDao {
        return database.patternDao()
    }

    @Provides
    fun provideSessionDao(database: JugCoachDatabase): SessionDao {
        return database.sessionDao()
    }

    @Provides
    fun provideNoteDao(database: JugCoachDatabase): NoteDao {
        return database.noteDao()
    }

    @Provides
    fun provideSettingsDao(database: JugCoachDatabase): SettingsDao {
        return database.settingsDao()
    }
}
