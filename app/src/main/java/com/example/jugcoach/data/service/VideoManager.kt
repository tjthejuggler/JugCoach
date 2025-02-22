package com.example.jugcoach.data.service

import android.content.Context
import androidx.work.Data
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.example.jugcoach.data.entity.Pattern
import com.example.jugcoach.data.worker.VideoDownloadWorker
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class VideoManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val workManager: WorkManager
) {
    companion object {
        const val MAX_CACHE_SIZE = 500 * 1024 * 1024L // 500MB
        const val CACHE_DIR = "video_cache"
        const val KEY_PATTERN_ID = "pattern_id"
        const val KEY_VIDEO_URL = "video_url"
    }

    private val cache = SimpleCache(
        File(context.cacheDir, CACHE_DIR),
        LeastRecentlyUsedCacheEvictor(MAX_CACHE_SIZE),
        StandaloneDatabaseProvider(context)
    )

    /**
     * Get the local file for a pattern's video
     */
    fun getVideoFile(pattern: Pattern): File {
        return File(context.cacheDir, "videos/${pattern.id}.mp4")
    }

    /**
     * Check if a video is already downloaded
     */
    fun isVideoDownloaded(pattern: Pattern): Boolean {
        return getVideoFile(pattern).exists()
    }

    /**
     * Schedule a video download
     */
    fun downloadVideo(pattern: Pattern) {
        pattern.video?.let { videoUrl ->
            val downloadWork = OneTimeWorkRequestBuilder<VideoDownloadWorker>()
                .setInputData(workDataOf(
                    KEY_PATTERN_ID to pattern.id,
                    KEY_VIDEO_URL to videoUrl
                ))
                .build()

            workManager.enqueue(downloadWork)
        }
    }

    /**
     * Clear all cached videos
     */
    fun clearCache() {
        cache.release()
        File(context.cacheDir, CACHE_DIR).deleteRecursively()
    }

    /**
     * Get the ExoPlayer cache instance
     */
    fun getCache() = cache
}