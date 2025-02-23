package com.example.jugcoach.ui.video

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.widget.FrameLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.core.view.isVisible
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import com.example.jugcoach.R
import com.example.jugcoach.data.entity.Pattern
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.PlayerView
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.YouTubePlayer
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.listeners.AbstractYouTubePlayerListener
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.views.YouTubePlayerView

class VideoPlayerView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr), DefaultLifecycleObserver {

    private lateinit var exoPlayer: ExoPlayer
    private var pattern: Pattern? = null
    private var youTubePlayer: YouTubePlayer? = null
    
    // UI components
    private val exoPlayerView: PlayerView
    private val youTubePlayerView: YouTubePlayerView
    private val loadingIndicator: ProgressBar
    private val errorMessage: TextView

    init {
        layoutParams = LayoutParams(
            LayoutParams.MATCH_PARENT,
            LayoutParams.MATCH_PARENT
        )

        LayoutInflater.from(context).inflate(R.layout.view_video_player, this, true)

        exoPlayerView = findViewById(R.id.exo_player_view)
        youTubePlayerView = findViewById(R.id.youtube_player_view)
        loadingIndicator = findViewById(R.id.loading_indicator)
        errorMessage = findViewById(R.id.error_message)

        // Configure YouTube player before initialization
        youTubePlayerView.enableAutomaticInitialization = false

        setupExoPlayer()
        
        // Initially hide all states
        exoPlayerView.isVisible = false
        youTubePlayerView.isVisible = false
        loadingIndicator.isVisible = false
        errorMessage.isVisible = false
    }

    private fun setupExoPlayer() {
        val dataSourceFactory = DefaultDataSource.Factory(context)
        val mediaSourceFactory = DefaultMediaSourceFactory(dataSourceFactory)
        
        exoPlayer = ExoPlayer.Builder(context)
            .setMediaSourceFactory(mediaSourceFactory)
            .build()
            .apply {
                videoScalingMode = C.VIDEO_SCALING_MODE_SCALE_TO_FIT
                exoPlayerView.player = this
                
                addListener(object : Player.Listener {
                    override fun onPlaybackStateChanged(state: Int) {
                        when (state) {
                            Player.STATE_BUFFERING -> {
                                exoPlayerView.isVisible = true
                                loadingIndicator.isVisible = true
                                errorMessage.isVisible = false
                            }
                            Player.STATE_READY -> {
                                exoPlayerView.isVisible = true
                                loadingIndicator.isVisible = false
                                errorMessage.isVisible = false
                            }
                            Player.STATE_IDLE -> {
                                exoPlayerView.isVisible = false
                                loadingIndicator.isVisible = false
                            }
                        }
                    }

                    override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                        showError(context.getString(R.string.error_loading_video, error.message))
                    }
                })
            }
    }

    fun bindToLifecycle(lifecycleOwner: LifecycleOwner) {
        lifecycleOwner.lifecycle.addObserver(youTubePlayerView)
        lifecycleOwner.lifecycle.addObserver(this)
        setupYouTubePlayer()
    }

    private fun setupYouTubePlayer() {
        if (youTubePlayer == null) {
            val listener = object : AbstractYouTubePlayerListener() {
                override fun onReady(player: YouTubePlayer) {
                    youTubePlayer = player
                    pattern?.video?.let { videoUrl ->
                        extractYouTubeId(videoUrl)?.let { videoId ->
                            loadYouTubeVideo(videoId)
                        }
                    }
                }

                override fun onError(player: YouTubePlayer, error: com.pierfrancescosoffritti.androidyoutubeplayer.core.player.PlayerConstants.PlayerError) {
                    showError(context.getString(R.string.error_loading_video, error.name))
                }
            }

            try {
                youTubePlayerView.initialize(listener, true)
            } catch (e: Exception) {
                showError(context.getString(R.string.error_loading_video, e.message))
            }
        }
    }

    fun setPattern(pattern: Pattern) {
        this.pattern = pattern
        
        showLoading()
        
        try {
            pattern.video?.let { videoUrl ->
                val videoId = extractYouTubeId(videoUrl)
                
                if (videoId != null) {
                    // YouTube video
                    exoPlayerView.isVisible = false
                    youTubePlayerView.isVisible = true
                    loadYouTubeVideo(videoId)
                } else {
                    // Direct video URL
                    youTubePlayerView.isVisible = false
                    exoPlayerView.isVisible = true
                    loadDirectVideo(videoUrl)
                }
            } ?: run {
                showError(context.getString(R.string.error_no_video_url))
            }
        } catch (e: Exception) {
            showError(context.getString(R.string.error_loading_video, e.message))
        }
    }

    private fun loadYouTubeVideo(videoId: String) {
        try {
            youTubePlayerView.isVisible = true
            exoPlayerView.isVisible = false
            youTubePlayer?.cueVideo(videoId, 0f)
            hideLoading()
        } catch (e: Exception) {
            showError(context.getString(R.string.error_loading_video, e.message))
        }
    }

    private fun loadDirectVideo(videoUrl: String) {
        val mediaItem = MediaItem.fromUri(videoUrl)
        exoPlayer.apply {
            setMediaItem(mediaItem)
            playWhenReady = true
            prepare()
        }
    }

    private fun extractYouTubeId(url: String): String? {
        val pattern = "(?<=watch\\?v=|/videos/|embed/|youtu.be/|/v/|/e/|watch\\?v%3D|watch\\?feature=player_embedded&v=)[^#&?\\n]*"
        val regex = Regex(pattern)
        return regex.find(url)?.value
    }

    private fun showLoading() {
        loadingIndicator.isVisible = true
        errorMessage.isVisible = false
    }

    private fun hideLoading() {
        loadingIndicator.isVisible = false
    }

    private fun showError(message: String) {
        exoPlayerView.isVisible = false
        youTubePlayerView.isVisible = false
        loadingIndicator.isVisible = false
        errorMessage.apply {
            isVisible = true
            text = message
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        pattern?.let { setPattern(it) }
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        releaseResources()
    }

    private fun releaseResources() {
        try {
            exoPlayer.release()
            youTubePlayer = null
            youTubePlayerView.release()
        } catch (e: Exception) {
            // Ignore release errors
        }
    }

    // Lifecycle methods
    override fun onResume(owner: LifecycleOwner) {
        if (youTubePlayerView.isVisible) {
            youTubePlayer?.play()
        }
    }

    override fun onStop(owner: LifecycleOwner) {
        if (youTubePlayerView.isVisible) {
            youTubePlayer?.pause()
        }
    }

    override fun onDestroy(owner: LifecycleOwner) {
        releaseResources()
    }
}