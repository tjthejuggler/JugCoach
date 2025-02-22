# Video Player Implementation Plan

## Overview
Replace YouTube video links with an embedded video player that supports loop sections and speed control, similar to pattern GIFs.

## UI Changes

### Video Player Component
- Create custom `VideoPlayerView` extending ExoPlayer
- Position in same location as current pattern_animation
- Support both video and GIF playback
- Maintain current aspect ratio and scaling behavior

### Playback Controls
- Play/Pause button
- Timeline with loop section markers (using videoStartTime/videoEndTime)
- Speed control slider (0.25x - 2x)
- Full screen toggle

## Technical Implementation

### ExoPlayer Integration
- Use ExoPlayer library for video playback
- Configure for:
  - Local file playback
  - Loop section support
  - Variable speed playback
  - Thumbnail generation

### Video Management
- Download videos from YouTube for local playback
  - Download on first pattern access
  - Store in app's private storage
- Cache management system
  - Set maximum cache size
  - LRU eviction policy
  - Clear cache option in settings

### Custom Controls
1. Loop Functionality
   - Use Pattern's videoStartTime and videoEndTime for loop boundaries
   - Automatically enable looping when times are defined
   - Use ExoPlayer's setRepeatMode and seekTo for implementation

2. Speed Control
   - Temporary speed adjustment during playback (0.25x - 2.0x)
   - Reset to normal speed when video is reloaded
   - Smooth transitions between speeds

## Data Model Updates

### Data Model
The Pattern entity already contains the necessary fields:
```kotlin
data class Pattern(
    // ... other fields ...
    val video: String?,          // YouTube URL
    val videoStartTime: Int?,    // Loop start time
    val videoEndTime: Int?,      // Loop end time
    // ... other fields ...
)
```

We'll store downloaded videos in the app's cache directory using the pattern ID as part of the filename:
```
context.cacheDir/videos/{pattern_id}.mp4
```

Video cache settings will be defined as constants:
```kotlin
object VideoCacheConfig {
    const val MAX_CACHE_SIZE = 500 * 1024 * 1024L // 500MB
    const val CACHE_DIR = "video_cache"
}
```

### Required Resources

#### Drawables
Add to `res/drawable`:
```xml
<!-- ic_play_pause.xml -->
<animated-selector xmlns:android="http://schemas.android.com/apk/res/android">
    <item
        android:id="@+id/playing"
        android:drawable="@drawable/ic_pause"
        android:state_checked="true"/>
    <item
        android:id="@+id/paused"
        android:drawable="@drawable/ic_play"/>
</animated-selector>

<!-- ic_loop.xml -->
<selector xmlns:android="http://schemas.android.com/apk/res/android">
    <item
        android:state_checked="true"
        android:drawable="@drawable/ic_loop_enabled"/>
    <item
        android:drawable="@drawable/ic_loop_disabled"/>
</selector>

<!-- video_controls_background.xml -->
<shape xmlns:android="http://schemas.android.com/apk/res/android">
    <gradient
        android:angle="90"
        android:startColor="#88000000"
        android:endColor="#00000000"/>
</shape>
```

#### Styles
Add to `res/values/styles.xml`:
```xml
<style name="VideoControlButton">
    <item name="android:layout_width">48dp</item>
    <item name="android:layout_height">48dp</item>
    <item name="android:background">?attr/selectableItemBackgroundBorderless</item>
    <item name="android:padding">12dp</item>
</style>
```

## Implementation Phases

### Phase 1: ExoPlayer Setup (1-2 days)
- [ ] Add dependencies to build.gradle.kts:
```kotlin
dependencies {
    // ExoPlayer
    implementation("com.google.android.exoplayer:exoplayer-core:2.19.1")
    implementation("com.google.android.exoplayer:exoplayer-ui:2.19.1")
    
    // WorkManager for background downloads
    implementation("androidx.work:work-runtime-ktx:2.9.0")
}
```
- [ ] Create VideoPlayerView component:
```kotlin
class VideoPlayerView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {
    private var player: ExoPlayer? = null
    private var pattern: Pattern? = null
    
    init {
        // Initialize ExoPlayer
        player = ExoPlayer.Builder(context).build().apply {
            // Add listener for loop control
            addListener(object : Player.Listener {
                override fun onPositionDiscontinuity(
                    oldPosition: Player.PositionInfo,
                    newPosition: Player.PositionInfo,
                    reason: Int
                ) {
                    if (reason == Player.DISCONTINUITY_REASON_AUTO_TRANSITION) {
                        // At end of loop, jump back to start
                        pattern?.videoStartTime?.let { startTime ->
                            seekTo(startTime.toLong())
                        }
                    }
                }
            })
        }
        
        // Setup speed control
        findViewById<Slider>(R.id.speed_control).apply {
            addOnChangeListener { _, value, _ ->
                player?.setPlaybackSpeed(value)
                findViewById<TextView>(R.id.speed_label).text =
                    String.format("%.1fx", value)
            }
        }
        
        // Setup play/pause button
        findViewById<ImageButton>(R.id.play_pause).apply {
            setOnClickListener {
                player?.let { exoPlayer ->
                    if (exoPlayer.isPlaying) {
                        exoPlayer.pause()
                        setImageResource(R.drawable.ic_play)
                    } else {
                        exoPlayer.play()
                        setImageResource(R.drawable.ic_pause)
                    }
                }
            }
        }
    }
    
    fun setPattern(pattern: Pattern) {
        this.pattern = pattern
        
        // Configure video source
        player?.let { exoPlayer ->
            val mediaItem = MediaItem.fromUri(pattern.video!!)
            exoPlayer.setMediaItem(mediaItem)
            
            // Set initial playback state
            exoPlayer.playWhenReady = true
            exoPlayer.repeatMode = Player.REPEAT_MODE_ONE
            
            // Reset speed to normal
            exoPlayer.setPlaybackSpeed(1.0f)
            findViewById<Slider>(R.id.speed_control).value = 1.0f
            
            // Prepare player
            exoPlayer.prepare()
            
            // Seek to start time if defined
            pattern.videoStartTime?.let { startTime ->
                exoPlayer.seekTo(startTime.toLong())
            }
        }
    }
    
    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        player?.release()
        player = null
    }
}
```
- [ ] Implement basic playback controls
- [ ] Add video cache management using ExoPlayer's cache

### Phase 2: Video Controls UI (2-3 days)
- [ ] Create custom layout for video player:
```xml
<merge xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:app="http://schemas.android.com/apk/res-auto">
    
    <!-- Video surface -->
    <com.google.android.exoplayer2.ui.StyledPlayerView
        android:id="@+id/player_view"
        android:layout_width="match_parent"
        android:layout_height="wrap_content"/>
        
    <!-- Custom controls -->
    <LinearLayout
        android:id="@+id/controls_container"
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:layout_gravity="bottom"
        android:background="@drawable/video_controls_background"
        android:orientation="vertical">
        
        <!-- Timeline -->
        <com.google.android.exoplayer2.ui.DefaultTimeBar
            android:id="@+id/time_bar"
            android:layout_width="match_parent"
            android:layout_height="wrap_content"/>
            
        <!-- Control buttons -->
        <LinearLayout
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:orientation="horizontal">
            
            <ImageButton
                android:id="@+id/play_pause"
                style="@style/VideoControlButton"
                android:src="@drawable/ic_play_pause"/>
                
            <Slider
                android:id="@+id/speed_control"
                android:layout_width="0dp"
                android:layout_weight="1"
                android:layout_height="wrap_content"
                android:layout_marginStart="8dp"
                android:valueFrom="0.25"
                android:valueTo="2.0"
                android:stepSize="0.25"/>
                
            <TextView
                android:id="@+id/speed_label"
                android:layout_width="wrap_content"
                android:layout_height="wrap_content"
                android:layout_marginStart="4dp"
                android:textSize="12sp"
                android:text="1.0x"/>
        </LinearLayout>
    </LinearLayout>
</merge>
```
- [ ] Implement loop markers on timeline
- [ ] Add speed control functionality
- [ ] Create loading and error states

### Phase 3: Video Management (2-3 days)
- [ ] Create VideoManager service:
```kotlin
@Singleton
class VideoManager @Inject constructor(
    private val context: Context,
    private val workManager: WorkManager
) {
    private val cache = SimpleCache(
        File(context.cacheDir, "video_cache"),
        LruCacheEvictor(500 * 1024 * 1024), // 500MB cache
        ExoDatabaseProvider(context)
    )
    
    fun getVideoFile(pattern: Pattern): File {
        return File(context.cacheDir, "videos/${pattern.id}.mp4")
    }
    
    fun downloadVideo(pattern: Pattern) {
        // Schedule download work
        val downloadWork = OneTimeWorkRequestBuilder<VideoDownloadWorker>()
            .setInputData(workDataOf(
                "pattern_id" to pattern.id,
                "video_url" to pattern.video
            ))
            .build()
            
        workManager.enqueue(downloadWork)
    }
}
```
- [ ] Implement VideoDownloadWorker
- [ ] Add download progress notifications
- [ ] Handle cache cleanup
### Phase 4: Integration (1-2 days)
- [ ] Update PatternDetailsFragment to use VideoPlayerView:
```kotlin
class PatternDetailsFragment : Fragment() {
    private var videoPlayer: VideoPlayerView? = null
    
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        // Replace video button with player
        videoPlayer = view.findViewById(R.id.video_player)
        view.findViewById<Button>(R.id.video_button).visibility = View.GONE
        
        // Observe pattern data
        viewModel.pattern.observe(viewLifecycleOwner) { pattern ->
            pattern?.let {
                if (it.video != null) {
                    videoPlayer?.visibility = View.VISIBLE
                    videoPlayer?.setPattern(it)
                } else {
                    videoPlayer?.visibility = View.GONE
                }
            }
        }
    }
    
    override fun onDestroyView() {
        super.onDestroyView()
        videoPlayer = null
    }
}
```
- [ ] Add download progress indicator
- [ ] Implement offline playback from cache
- [ ] Handle offline mode

### Phase 5: Testing and Polish (2 days)
- [ ] Unit tests for VideoManager
- [ ] Integration tests for video playback
- [ ] UI tests for video controls
- [ ] Performance optimization
  - Memory usage
  - Cache efficiency
  - Battery impact

## Future Enhancements

### Pattern Analysis Tools
- Frame-by-frame analysis with throw/catch detection
- Ball path tracking and visualization
- Automatic pattern recognition and validation
- Catch/throw timing analysis
- Side-by-side comparison with reference videos

### Training Features
- Multiple loop sections per pattern with labels
- Practice sequence builder with multiple patterns
- Export loop sections as GIFs for sharing
- Video bookmarks with notes
- Slow-motion playback with frame interpolation

### Learning Tools
- AI-powered technique feedback
- Pattern difficulty analysis from video
- Automatic catch counting
- Progress tracking with video evidence
- Compare your execution with reference patterns