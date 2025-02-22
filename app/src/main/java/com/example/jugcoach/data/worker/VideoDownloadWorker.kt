package com.example.jugcoach.data.worker

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.example.jugcoach.R
import com.example.jugcoach.data.service.VideoManager.Companion.KEY_PATTERN_ID
import com.example.jugcoach.data.service.VideoManager.Companion.KEY_VIDEO_URL
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

@HiltWorker
class VideoDownloadWorker @AssistedInject constructor(
    @Assisted private val context: Context,
    @Assisted private val params: WorkerParameters,
    private val okHttpClient: OkHttpClient
) : CoroutineWorker(context, params) {

    companion object {
        private const val CHANNEL_ID = "video_download_channel"
        private const val NOTIFICATION_ID = 1
        private const val BUFFER_SIZE = 8192
    }

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val patternId = inputData.getString(KEY_PATTERN_ID)
        val videoUrl = inputData.getString(KEY_VIDEO_URL)

        if (patternId == null || videoUrl == null) {
            return@withContext Result.failure()
        }

        val outputFile = File(context.cacheDir, "videos/${patternId}.mp4")
        outputFile.parentFile?.mkdirs()

        try {
            createNotificationChannel()
            val notification = createProgressNotification(0)
            setForeground(androidx.work.ForegroundInfo(NOTIFICATION_ID, notification))

            val request = Request.Builder()
                .url(videoUrl)
                .build()

            okHttpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    throw IOException("Unexpected response: ${response.code}")
                }

                val body = response.body ?: throw IOException("Empty response body")
                val contentLength = body.contentLength()
                var bytesWritten = 0L

                FileOutputStream(outputFile).use { output ->
                    body.byteStream().use { input ->
                        val buffer = ByteArray(BUFFER_SIZE)
                        var bytes = input.read(buffer)
                        
                        while (bytes >= 0) {
                            output.write(buffer, 0, bytes)
                            bytesWritten += bytes
                            bytes = input.read(buffer)

                            // Update progress notification and work progress
                            if (contentLength > 0) {
                                val progress = ((bytesWritten * 100) / contentLength).toInt()
                                setForeground(
                                    androidx.work.ForegroundInfo(
                                        NOTIFICATION_ID,
                                        createProgressNotification(progress)
                                    )
                                )
                                // Report progress to observers
                                setProgress(workDataOf("progress" to progress))
                            }
                        }
                    }
                }
            }

            // Show completion notification
            setForeground(
                androidx.work.ForegroundInfo(
                    NOTIFICATION_ID,
                    createCompletionNotification()
                )
            )

            Result.success()
        } catch (e: Exception) {
            e.printStackTrace()
            // Show error notification
            setForeground(
                androidx.work.ForegroundInfo(
                    NOTIFICATION_ID,
                    createErrorNotification(e.message ?: "Download failed")
                )
            )
            Result.failure(workDataOf("error_message" to e.message))
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Video Downloads",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows video download progress"
            }

            val notificationManager = context.getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun createProgressNotification(progress: Int) = NotificationCompat.Builder(context, CHANNEL_ID)
        .setSmallIcon(android.R.drawable.stat_sys_download)
        .setContentTitle("Downloading video")
        .setProgress(100, progress, progress == 0)
        .setOngoing(true)
        .build()

    private fun createCompletionNotification() = NotificationCompat.Builder(context, CHANNEL_ID)
        .setSmallIcon(android.R.drawable.stat_sys_download_done)
        .setContentTitle("Video downloaded")
        .setAutoCancel(true)
        .build()

    private fun createErrorNotification(error: String) = NotificationCompat.Builder(context, CHANNEL_ID)
        .setSmallIcon(android.R.drawable.stat_notify_error)
        .setContentTitle("Download failed")
        .setContentText(error)
        .setAutoCancel(true)
        .build()
}