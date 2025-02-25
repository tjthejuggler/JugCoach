package com.example.jugcoach.ui.video

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.widget.FrameLayout
import android.widget.ImageButton
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
import com.google.android.material.slider.Slider
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
    private val speedSlider: Slider
    private val speedLabel: TextView
    private val playPauseButton: ImageButton
    private var isPlaying = false
    private var loadError = false

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
        speedSlider = findViewById(R.id.speed_slider)
        speedLabel = findViewById(R.id.speed_label)
        playPauseButton = findViewById(R.id.play_pause_button)

        // Configure YouTube player before initialization
        youTubePlayerView.enableAutomaticInitialization = false

        setupExoPlayer()
        setupControls()
        
        // Initially hide all states
        exoPlayerView.isVisible = false
        youTubePlayerView.isVisible = false
        loadingIndicator.isVisible = false
        errorMessage.isVisible = false
    }

    private fun setupControls() {
        // Setup speed control
        speedSlider.apply {
            valueFrom = 0.25f
            valueTo = 2.0f
            stepSize = 0.25f
            value = 1.0f
            
            addOnChangeListener { _, value, _ ->
                speedLabel.text = String.format("%.2fx", value)
                exoPlayer.setPlaybackSpeed(value)
                // For YouTube we need special handling for speed
                youTubePlayer?.let { player ->
                    // The YouTube API has limited playback rate options, map our slider to them
                    val rate = when {
                        value <= 0.25f -> com.pierfrancescosoffritti.androidyoutubeplayer.core.player.PlayerConstants.PlaybackRate.RATE_0_25
                        value <= 0.5f -> com.pierfrancescosoffritti.androidyoutubeplayer.core.player.PlayerConstants.PlaybackRate.RATE_0_5
                        value <= 0.75f -> com.pierfrancescosoffritti.androidyoutubeplayer.core.player.PlayerConstants.PlaybackRate.RATE_0_5 // No 0.75 rate, use 0.5
                        value <= 1.0f -> com.pierfrancescosoffritti.androidyoutubeplayer.core.player.PlayerConstants.PlaybackRate.RATE_1  // Normal rate is 1
                        value <= 1.5f -> com.pierfrancescosoffritti.androidyoutubeplayer.core.player.PlayerConstants.PlaybackRate.RATE_1_5
                        else -> com.pierfrancescosoffritti.androidyoutubeplayer.core.player.PlayerConstants.PlaybackRate.RATE_2
                    }
                    player.setPlaybackRate(rate)
                }
            }
        }
        
        // Setup play/pause button
        playPauseButton.setOnClickListener {
            if (isPlaying) {
                pausePlayback()
            } else {
                startPlayback()
            }
        }
    }

    private fun startPlayback() {
        isPlaying = true
        playPauseButton.setImageResource(R.drawable.ic_pause)
        exoPlayer.play()
        youTubePlayer?.play()
    }
    
    private fun pausePlayback() {
        isPlaying = false
        playPauseButton.setImageResource(R.drawable.ic_play)
        exoPlayer.pause()
        youTubePlayer?.pause()
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
                                
                                // Apply loop settings if available
                                pattern?.let { p ->
                                    if (p.videoStartTime != null || p.videoEndTime != null) {
                                        setupLooping(p.videoStartTime, p.videoEndTime)
                                    }
                                }
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
                    
                    override fun onPositionDiscontinuity(
                        oldPosition: Player.PositionInfo,
                        newPosition: Player.PositionInfo,
                        reason: Int
                    ) {
                        // Handle looping based on custom start/end times
                        if (reason == Player.DISCONTINUITY_REASON_AUTO_TRANSITION) {
                            pattern?.videoStartTime?.let { startMs ->
                                // When reaching the end of video (or end time), seek back to start time
                                exoPlayer.seekTo(startMs.toLong())
                            }
                        }
                    }
                })
            }
    }

    private fun setupLooping(startTimeSeconds: Int?, endTimeSeconds: Int?) {
        // Set loop mode
        exoPlayer.repeatMode = Player.REPEAT_MODE_ONE
        
        // If we have a start time, seek to it
        startTimeSeconds?.let {
            exoPlayer.seekTo(it * 1000L)
        }
        
        // If we have an end time, we need to monitor the current position
        if (endTimeSeconds != null) {
            val endMs = endTimeSeconds * 1000L
            
            // Remove any existing listener by using a new one
            exoPlayer.removeListener(loopPositionListener)
            
            // Set and add a listener for position
            loopPositionListener = object : Player.Listener {
                override fun onPositionDiscontinuity(
                    oldPosition: Player.PositionInfo,
                    newPosition: Player.PositionInfo,
                    reason: Int
                ) {
                    super.onPositionDiscontinuity(oldPosition, newPosition, reason)
                    
                    // Handle manual seek or end of media
                    if (reason == Player.DISCONTINUITY_REASON_SEEK ||
                        reason == Player.DISCONTINUITY_REASON_AUTO_TRANSITION) {
                        
                        startTimeSeconds?.let { startSec ->
                            exoPlayer.seekTo(startSec * 1000L)
                        }
                    }
                }
                
                override fun onPlaybackStateChanged(state: Int) {
                    super.onPlaybackStateChanged(state)
                    if (state == Player.STATE_READY) {
                        // Start a runnable to check position periodically
                        handler.post(loopCheckRunnable)
                    } else if (state == Player.STATE_ENDED || state == Player.STATE_IDLE) {
                        handler.removeCallbacks(loopCheckRunnable)
                    }
                }
            }
            
            exoPlayer.addListener(loopPositionListener)
        }
    }
    
    // Used for custom loop timing
    private var loopPositionListener: Player.Listener = object : Player.Listener {}
    private val handler = android.os.Handler(android.os.Looper.getMainLooper())
    private val loopCheckRunnable = object : Runnable {
        override fun run() {
            pattern?.videoEndTime?.let { endSec ->
                val endMs = endSec * 1000L
                if (exoPlayer.currentPosition >= endMs) {
                    pattern?.videoStartTime?.let { startSec ->
                        exoPlayer.seekTo(startSec * 1000L)
                    } ?: exoPlayer.seekTo(0)
                }
            }
            // Check position every 100ms
            handler.postDelayed(this, 100)
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
                
                override fun onStateChange(player: YouTubePlayer, state: com.pierfrancescosoffritti.androidyoutubeplayer.core.player.PlayerConstants.PlayerState) {
                    if (state == com.pierfrancescosoffritti.androidyoutubeplayer.core.player.PlayerConstants.PlayerState.ENDED) {
                        // When video ends, restart from start time if available
                        pattern?.videoStartTime?.let { startTime ->
                            player.seekTo(startTime.toFloat())
                        } ?: player.seekTo(0f)
                    }
                }
                
                override fun onCurrentSecond(player: YouTubePlayer, second: Float) {
                    // Handle custom end time for looping
                    pattern?.videoEndTime?.let { endTime ->
                        if (second >= endTime) {
                            pattern?.videoStartTime?.let { startTime ->
                                player.seekTo(startTime.toFloat())
                            } ?: player.seekTo(0f)
                        }
                    }
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
        loadError = false
        
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
            
            // Apply loop and start time settings
            youTubePlayer?.let { player ->
                player.cueVideo(videoId, pattern?.videoStartTime?.toFloat() ?: 0f)
                
                // Set to looping mode
                if (pattern?.videoStartTime != null || pattern?.videoEndTime != null) {
                    // The looping is handled in the onCurrentSecond and onStateChange callbacks
                }
                
                // Start playing if needed
                if (isPlaying) {
                    player.play()
                }
            }
            
            hideLoading()
        } catch (e: Exception) {
            showError(context.getString(R.string.error_loading_video, e.message))
        }
    }

    private fun loadDirectVideo(videoUrl: String) {
        try {
            val mediaItem = MediaItem.fromUri(videoUrl)
            exoPlayer.apply {
                setMediaItem(mediaItem)
                playWhenReady = isPlaying
                prepare()
                
                // Apply loop settings if available
                pattern?.let { p ->
                    if (p.videoStartTime != null || p.videoEndTime != null) {
                        setupLooping(p.videoStartTime, p.videoEndTime)
                    }
                }
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
        loadError = true
        
        // Make the entire view invisible when there's an error
        this.isVisible = false
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
            // Stop any running handlers and listeners
            handler.removeCallbacks(loopCheckRunnable)
            exoPlayer.removeListener(loopPositionListener)
            
            // Release main resources
            exoPlayer.release()
            youTubePlayer = null
            youTubePlayerView.release()
        } catch (e: Exception) {
            // Ignore release errors
        }
    }

    // Lifecycle methods
    override fun onResume(owner: LifecycleOwner) {
        if (isPlaying) {
            if (youTubePlayerView.isVisible) {
                youTubePlayer?.play()
            } else if (exoPlayerView.isVisible) {
                exoPlayer.play()
            }
        }
    }

    override fun onStop(owner: LifecycleOwner) {
        if (youTubePlayerView.isVisible) {
            youTubePlayer?.pause()
        }
        if (exoPlayerView.isVisible) {
            exoPlayer.pause()
        }
    }

    override fun onDestroy(owner: LifecycleOwner) {
        releaseResources()
    }
    
    fun hasError(): Boolean {
        return loadError
    }
}