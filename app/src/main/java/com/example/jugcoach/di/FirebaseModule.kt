package com.example.jugcoach.di

import android.content.Context
import com.example.jugcoach.data.firebase.AuthRepository
import com.example.jugcoach.data.firebase.FirebaseManager
import com.example.jugcoach.data.firebase.RecordSyncRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object FirebaseModule {
    
    @Provides
    @Singleton
    fun provideFirebaseManager(
        @ApplicationContext context: Context
    ): FirebaseManager {
        return FirebaseManager(context)
    }
    
    @Provides
    @Singleton
    fun provideAuthRepository(
        firebaseManager: FirebaseManager
    ): AuthRepository {
        return AuthRepository(firebaseManager)
    }
    
    @Provides
    @Singleton
    fun provideRecordSyncRepository(
        firebaseManager: FirebaseManager,
        authRepository: AuthRepository
    ): RecordSyncRepository {
        return RecordSyncRepository(firebaseManager, authRepository)
    }
}