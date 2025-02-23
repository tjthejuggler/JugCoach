package com.example.jugcoach.ui.video

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.ProgressBar
import android.widget.TextView
import androidx.core.view.isVisible
import com.example.jugcoach.R
import com.example.jugcoach.data.entity.Pattern
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.TimeBar
import androidx.media3.ui.PlayerView
import com.google.android.material.slider.Slider

class VideoPlayerView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

    private lateinit var player: ExoPlayer
    private var pattern: Pattern? = null
    
    // UI components
    private val playerView: PlayerView
    private val loadingIndicator: ProgressBar
    private val errorMessage: TextView

    init {
        // Set layout parameters for this view
        layoutParams = LayoutParams(
            LayoutParams.MATCH_PARENT,
            LayoutParams.MATCH_PARENT
        )

        // Inflate layout
        LayoutInflater.from(context).inflate(R.layout.view_video_player, this, true)

        // Initialize UI components
        playerView = findViewById(R.id.player_view)
        loadingIndicator = findViewById(R.id.loading_indicator)
        errorMessage = findViewById(R.id.error_message)

        setupPlayer()
        
        // Initially hide all states
        playerView.isVisible = false
        loadingIndicator.isVisible = false
        errorMessage.isVisible = false
    }

    private fun setupPlayer() {
        // Initialize player
        val dataSourceFactory = DefaultDataSource.Factory(context)
        val mediaSourceFactory = DefaultMediaSourceFactory(dataSourceFactory)
        
        player = ExoPlayer.Builder(context)
            .setMediaSourceFactory(mediaSourceFactory)
            .build()
            .apply {
                // Set video scaling mode
                videoScalingMode = C.VIDEO_SCALING_MODE_SCALE_TO_FIT

                // Set player to PlayerView
                playerView.player = this
                
                // Configure player for YouTube
                playerView.setShowBuffering(PlayerView.SHOW_BUFFERING_ALWAYS)
                
                // Add listener for basic state handling
                addListener(object : Player.Listener {
                    override fun onPlaybackStateChanged(state: Int) {
                        when (state) {
                            Player.STATE_BUFFERING -> {
                                playerView.isVisible = true
                                loadingIndicator.isVisible = true
                                errorMessage.isVisible = false
                            }
                            Player.STATE_READY -> {
                                playerView.isVisible = true
                                loadingIndicator.isVisible = false
                                errorMessage.isVisible = false
                            }
                            Player.STATE_IDLE -> {
                                playerView.isVisible = false
                                loadingIndicator.isVisible = false
                            }
                        }
                    }

                    override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                        playerView.isVisible = false
                        loadingIndicator.isVisible = false
                        errorMessage.apply {
                            isVisible = true
                            text = context.getString(R.string.error_loading_video, error.message)
                        }
                    }
                })
            }
    }

    fun setPattern(pattern: Pattern) {
        this.pattern = pattern
        
        // Show loading state
        playerView.isVisible = true
        loadingIndicator.isVisible = true
        errorMessage.isVisible = false
        
        try {
            pattern.video?.let { videoUrl ->
                // Extract video ID from YouTube URL if it's a YouTube video
                val videoId = if (videoUrl.contains("youtube.com") || videoUrl.contains("youtu.be")) {
                    extractYouTubeId(videoUrl)
                } else null

                // Create media item based on URL type
                val mediaItem = MediaItem.Builder().apply {
                    if (videoId != null) {
                        // YouTube video
                        setUri("https://www.youtube.com/watch?v=$videoId")
                    } else {
                        // Direct video URL
                        setUri(videoUrl)
                    }
                }.build()

                // Configure player
                player.apply {
                    setMediaItem(mediaItem)
                    playWhenReady = true
                    prepare()
                }
            } ?: run {
                showError(context.getString(R.string.error_no_video_url))
            }
        } catch (e: Exception) {
            showError(context.getString(R.string.error_loading_video, e.message))
        }
    }

    private fun extractYouTubeId(url: String): String? {
        val pattern = "(?<=watch\\?v=|/videos/|embed/|youtu.be/|/v/|/e/|watch\\?v%3D|watch\\?feature=player_embedded&v=)[^#&?\\n]*"
        val regex = Regex(pattern)
        return regex.find(url)?.value
    }

    private fun showError(message: String) {
        playerView.isVisible = false
        loadingIndicator.isVisible = false
        errorMessage.apply {
            isVisible = true
            text = message
        }
    }

    fun preparePlayer() {
        if (pattern != null) {
            player.prepare()
        }
    }

    fun releasePlayer() {
        player.release()
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        preparePlayer()
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        releasePlayer()
    }
}