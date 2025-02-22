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
import androidx.media3.exoplayer.ExoPlayer
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
    private val playPauseButton: ImageButton
    private val speedControl: Slider
    private val speedLabel: TextView
    private val timeBar: TimeBar
    private val loadingContainer: View
    private val loadingIndicator: ProgressBar
    private val loadingText: TextView
    private val errorMessage: TextView
    private val controlsContainer: View

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
        playPauseButton = findViewById(R.id.play_pause)
        speedControl = findViewById(R.id.speed_control)
        speedLabel = findViewById(R.id.speed_label)
        timeBar = findViewById(R.id.time_bar)
        loadingContainer = findViewById(R.id.loading_container)
        loadingIndicator = findViewById(R.id.loading_indicator)
        loadingText = findViewById(R.id.loading_text)
        errorMessage = findViewById(R.id.error_message)
        controlsContainer = findViewById(R.id.controls_container)

        setupPlayer()
        setupControls()
        
        // Initially hide all states
        playerView.isVisible = false
        controlsContainer.isVisible = false
        loadingContainer.isVisible = false
        errorMessage.isVisible = false
    }

    private fun setupPlayer() {
        // Initialize player with surface view provider
        player = ExoPlayer.Builder(context)
            .build()
            .apply {
                // Set video scaling mode
                videoScalingMode = C.VIDEO_SCALING_MODE_SCALE_TO_FIT

                // Set player to PlayerView
                playerView.player = this

                // Add listener for loop control
                addListener(object : Player.Listener {
                    override fun onPlaybackStateChanged(state: Int) {
                        when (state) {
                            Player.STATE_BUFFERING -> {
                                playerView.isVisible = true
                                loadingContainer.isVisible = true
                                loadingText.text = context.getString(R.string.loading_video)
                                errorMessage.isVisible = false
                                controlsContainer.isVisible = false
                            }
                            Player.STATE_READY -> {
                                playerView.isVisible = true
                                loadingContainer.isVisible = false
                                errorMessage.isVisible = false
                                controlsContainer.isVisible = true
                            }
                            Player.STATE_ENDED -> {
                                // Reset to start time if defined
                                pattern?.videoStartTime?.let { startTime ->
                                    seekTo(startTime.toLong())
                                    play()
                                }
                            }
                            Player.STATE_IDLE -> {
                                playerView.isVisible = false
                                loadingContainer.isVisible = false
                                controlsContainer.isVisible = false
                            }
                        }
                    }

                    override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                        playerView.isVisible = false
                        loadingContainer.isVisible = false
                        loadingIndicator.isVisible = false
                        controlsContainer.isVisible = false
                        errorMessage.apply {
                            isVisible = true
                            text = context.getString(R.string.error_loading_video, error.message)
                        }
                    }

                    override fun onIsPlayingChanged(isPlaying: Boolean) {
                        playPauseButton.setImageResource(
                            if (isPlaying) R.drawable.ic_pause else R.drawable.ic_play
                        )
                    }

                    override fun onPositionDiscontinuity(
                        oldPosition: Player.PositionInfo,
                        newPosition: Player.PositionInfo,
                        reason: Int
                    ) {
                        if (reason == Player.DISCONTINUITY_REASON_AUTO_TRANSITION) {
                            // At end of loop, jump back to start
                            pattern?.let { currentPattern ->
                                if (currentPattern.videoStartTime != null && 
                                    currentPattern.videoEndTime != null &&
                                    newPosition.positionMs >= currentPattern.videoEndTime) {
                                    seekTo(currentPattern.videoStartTime.toLong())
                                }
                            }
                        }
                    }
                })
            }
    }

    private fun setupControls() {
        // Play/Pause button
        playPauseButton.setOnClickListener {
            if (player.isPlaying) {
                player.pause()
            } else {
                player.play()
            }
        }

        // Speed control
        speedControl.addOnChangeListener { _, value, fromUser ->
            if (fromUser) {
                player.setPlaybackSpeed(value)
                speedLabel.text = String.format("%.1fx", value)
            }
        }

        // Time bar
        timeBar.addListener(object : TimeBar.OnScrubListener {
            override fun onScrubStart(timeBar: TimeBar, position: Long) {
                player.pause()
            }

            override fun onScrubMove(timeBar: TimeBar, position: Long) {
                player.seekTo(position)
            }

            override fun onScrubStop(timeBar: TimeBar, position: Long, canceled: Boolean) {
                if (!canceled) {
                    player.seekTo(position)
                }
                player.play()
            }
        })
    }

    fun setPattern(pattern: Pattern) {
        this.pattern = pattern
        
        // Show loading state
        playerView.isVisible = true
        loadingContainer.isVisible = true
        loadingText.text = context.getString(R.string.loading_video)
        errorMessage.isVisible = false
        controlsContainer.isVisible = false
        
        try {
            pattern.video?.let { videoUrl ->
                // Create media source
                val mediaItem = MediaItem.Builder()
                    .setUri(videoUrl)
                    .setClipStartPositionMs(pattern.videoStartTime?.toLong() ?: 0)
                    .apply {
                        pattern.videoEndTime?.let { endTime ->
                            setClipEndPositionMs(endTime.toLong())
                        }
                    }
                    .build()

                // Configure player
                player.apply {
                    setMediaItem(mediaItem)
                    playWhenReady = true
                    repeatMode = if (pattern.videoStartTime != null && pattern.videoEndTime != null) {
                        Player.REPEAT_MODE_ONE
                    } else {
                        Player.REPEAT_MODE_OFF
                    }
                    
                    // Reset speed to normal
                    setPlaybackSpeed(1.0f)
                    speedControl.value = 1.0f
                    speedLabel.text = "1.0x"
                    
                    // Prepare and start playback
                    prepare()
                }
            } ?: run {
                showError(context.getString(R.string.error_no_video_url))
            }
        } catch (e: Exception) {
            showError(context.getString(R.string.error_loading_video, e.message))
        }
    }

    private fun showError(message: String) {
        playerView.isVisible = false
        loadingContainer.isVisible = false
        controlsContainer.isVisible = false
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