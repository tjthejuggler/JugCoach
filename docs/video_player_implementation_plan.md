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
- Loop controls:
  - Start time selector 
  - End time selector
  - Loop enable/disable
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
   - Store loop points in milliseconds
   - Use ExoPlayer's setRepeatMode
   - Implement seekTo for loop boundaries
   - Save loop settings per pattern

2. Speed Control
   - Use setPlaybackSpeed
   - Save default speed per pattern
   - Smooth speed transitions

## Data Model Updates

### Data Model
Create new VideoSettings entity:
```kotlin
@Entity(
    tableName = "video_settings",
    foreignKeys = [
        ForeignKey(
            entity = Pattern::class,
            parentColumns = ["id"],
            childColumns = ["patternId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["patternId"])]
)
data class VideoSettings(
    @PrimaryKey
    val patternId: String,          // References Pattern.id
    val localVideoPath: String?,    // Path to downloaded video
    val loopStart: Long?,           // Loop start time in ms
    val loopEnd: Long?,            // Loop end time in ms
    val playbackSpeed: Float?,      // Playback speed (1.0 = normal)
    val lastPlaybackPosition: Long? // Remember where user left off
)
```

The existing Pattern.video field will continue storing the YouTube URL.

### Database Migration
1. Add new columns
2. Update DAO
3. Create migration path
4. Handle null values for existing patterns

## Implementation Phases

### Phase 1: Database and Foundation (1-2 days)
- [ ] Create VideoSettings entity and DAO
- [ ] Add Room migration script
- [ ] Setup ExoPlayer dependencies
- [ ] Create VideoPlayerView component
  - Base ExoPlayer implementation
  - Handling lifecycle events
  - Basic playback controls

### Phase 2: Video Management (2-3 days)
- [ ] Create VideoDownloadService
  - YouTube video downloading
  - Cache management
  - Background download support
  - Notification progress updates
- [ ] Add WorkManager for download scheduling
- [ ] Implement retry mechanism for failed downloads
- [ ] Add settings for cache size limits

### Phase 3: UI Components (2-3 days)
- [ ] Create video controls layout:
```xml
<LinearLayout android:id="@+id/video_controls">
    <!-- Timeline with loop markers -->
    <com.example.jugcoach.ui.video.VideoTimelineView
        android:layout_width="match_parent"
        android:layout_height="wrap_content"/>
    
    <!-- Control buttons -->
    <HorizontalScrollView>
        <LinearLayout>
            <ImageButton android:id="@+id/play_pause"/>
            <ImageButton android:id="@+id/loop_toggle"/>
            <ImageButton android:id="@+id/set_loop_start"/>
            <ImageButton android:id="@+id/set_loop_end"/>
        </LinearLayout>
    </HorizontalScrollView>
    
    <!-- Speed control -->
    <LinearLayout>
        <TextView android:id="@+id/speed_label"/>
        <Slider android:id="@+id/speed_slider"/>
    </LinearLayout>
</LinearLayout>
```
- [ ] Create custom VideoTimelineView for loop section visualization
- [ ] Implement gesture controls (double tap to seek, pinch to zoom timeline)
- [ ] Add loading and error states

### Phase 4: Integration (2-3 days)
- [ ] Update PatternDetailsFragment
  - Replace video button with VideoPlayerView
  - Handle video download states
  - Save/restore playback state
- [ ] Create VideoSettingsRepository
- [ ] Add video settings to pattern details menu
- [ ] Implement offline mode support

### Phase 5: Testing and Polish (2-3 days)
- [ ] Unit tests for VideoSettings DAO
- [ ] Integration tests for video playback
- [ ] UI tests for video controls
- [ ] Performance testing
  - Memory usage monitoring
  - Cache effectiveness
  - Download speeds
- [ ] User testing with various video types

## Future Enhancements
- Video trimming capability
- Multiple loop sections per video
- Frame-by-frame analysis
- Side-by-side comparison with GIF
- Export loop sections as GIFs