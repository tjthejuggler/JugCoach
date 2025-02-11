package com.example.jugcoach.di

import com.example.jugcoach.data.api.AnthropicService
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    @Provides
    @Singleton
    fun provideOkHttpClient(): OkHttpClient {
        val loggingInterceptor = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BODY
        }

        return OkHttpClient.Builder()
            .addInterceptor(loggingInterceptor)
            .addInterceptor { chain ->
                val request = chain.request()
                android.util.Log.d("NetworkModule", "Processing request: ${request.url}")

                // Get API key from x-api-key header
                val apiKey = request.header("x-api-key")
                android.util.Log.d("NetworkModule", "API key present: ${!apiKey.isNullOrEmpty()}")
                android.util.Log.d("NetworkModule", "Request URL: ${request.url}")
                android.util.Log.d("NetworkModule", "Original headers: ${request.headers}")
                android.util.Log.d("NetworkModule", "Request method: ${request.method}")
                android.util.Log.d("NetworkModule", "Request body size: ${request.body?.contentLength() ?: 0}")

                // Build new request with required headers
                val newRequest = request.newBuilder().apply {
                    header("Content-Type", "application/json")
                    header("Accept", "application/json")
                    header("anthropic-version", "2023-06-01")

                    // Only set Authorization if API key is present
                    if (!apiKey.isNullOrEmpty()) {
                        android.util.Log.d("NetworkModule", "Using API key: ${apiKey.take(10)}...${apiKey.takeLast(4)}")
                        header("x-api-key", apiKey)
                    } else {
                        android.util.Log.e("NetworkModule", "No API key found in request")
                    }
                }.build()

                // Log complete request details
                android.util.Log.d("NetworkModule", "=== Sending Request ===")
                android.util.Log.d("NetworkModule", "URL: ${newRequest.url}")
                android.util.Log.d("NetworkModule", "Method: ${newRequest.method}")
                android.util.Log.d("NetworkModule", "Headers:")
                for (i in 0 until newRequest.headers.size) {
                    val name = newRequest.headers.name(i)
                    val value = newRequest.headers.value(i)
                    android.util.Log.d("NetworkModule", "  $name: $value")
                }
                newRequest.body?.let { body ->
                    val bodyString = okio.Buffer().also { body.writeTo(it) }.readUtf8()
                    android.util.Log.d("NetworkModule", "Body: $bodyString")
                }
                android.util.Log.d("NetworkModule", "===================")

                try {
                    val response = chain.proceed(newRequest)
                    android.util.Log.d("NetworkModule", "Response code: ${response.code}")
                    android.util.Log.d("NetworkModule", "Response message: ${response.message}")
                    android.util.Log.d("NetworkModule", "Response headers: ${response.headers}")
                    
                    if (!response.isSuccessful) {
                        // Create a copy of the response body before consuming it
                        val responseBody = response.peekBody(Long.MAX_VALUE)
                        android.util.Log.e("NetworkModule", "Error response: ${responseBody.string()}")
                    }
                    
                    response
                } catch (e: Exception) {
                    android.util.Log.e("NetworkModule", "Network error", e)
                    android.util.Log.e("NetworkModule", "Error message: ${e.message}")
                    android.util.Log.e("NetworkModule", "Error cause: ${e.cause}")
                    throw e
                }
            }
            .connectTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
            .readTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
            .writeTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
            .build()
    }

    @Provides
    @Singleton
    fun provideRetrofit(okHttpClient: OkHttpClient): Retrofit {
        return Retrofit.Builder()
            .baseUrl("https://api.anthropic.com/v1/")
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
    }

    @Provides
    @Singleton
    fun provideAnthropicService(retrofit: Retrofit): AnthropicService {
        return retrofit.create(AnthropicService::class.java)
    }
}
