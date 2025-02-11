package com.example.jugcoach.di

import android.content.Context
import com.example.jugcoach.data.JugCoachDatabase
import com.example.jugcoach.data.dao.*
import com.example.jugcoach.data.importer.PatternImporter
import com.example.jugcoach.data.service.PatternDatabaseService
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
    fun providePatternDatabaseService(patternDao: PatternDao): PatternDatabaseService {
        return PatternDatabaseService(patternDao)
    }


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

    @Provides
    fun provideCoachDao(database: JugCoachDatabase): CoachDao {
        return database.coachDao()
    }

    @Provides
    fun provideConversationDao(database: JugCoachDatabase): ConversationDao {
        return database.conversationDao()
    }

    @Provides
    fun provideChatMessageDao(database: JugCoachDatabase): ChatMessageDao {
        return database.chatMessageDao()
    }

    @Provides
    fun provideCoachProposalDao(database: JugCoachDatabase): CoachProposalDao {
        return database.coachProposalDao()
    }

    @Provides
    fun providePatternImporter(
        @ApplicationContext context: Context,
        patternDao: PatternDao
    ): PatternImporter {
        return PatternImporter(context, patternDao)
    }
}
