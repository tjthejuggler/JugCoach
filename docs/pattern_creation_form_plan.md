# Pattern Creation Form Implementation Plan

## Overview
Add a pattern creation form accessible via a FAB in the patterns screen, allowing users to input all pattern information in a convenient way.

## UI Components

### 1. Floating Action Button (FAB)
- Add a FAB with + icon in the patterns screen
- Position in bottom right corner
- Use Material Design guidelines for FAB implementation

### 2. Pattern Creation Bottom Sheet
Create a new fragment: `CreatePatternBottomSheetFragment`

#### Basic Information Section
- Name field (EditText, required)
  * Auto-completion for existing pattern names
  * Auto-detect siteswap from name
  * Validation for duplicate patterns
  * Error message display
- Number of balls selection (ChipGroup)
  * Chips for 1-11 balls
  * Auto-populated from siteswap if detected
  * Disabled when valid siteswap is detected
- Difficulty slider (Slider)
  * Range 1-10
  * Show current value
  * Auto-suggested from siteswap complexity if detected
- Siteswap field (EditText)
  * Optional
  * Validation for siteswap format
  * Auto-populates number of balls
  * Adds "pure-ss" tag automatically for pure siteswaps

#### Media Section
- Video input
  * Support for YouTube and Instagram URLs
  * URL validation
  * Optional time range specification
  * Time format validation (mm:ss)
  * Preview capability
- GIF URL
  * Support for Juggling Lab URLs
  * Auto-populated for pure siteswaps
  * Preview capability
- Tutorial URL
  * Optional
  * URL validation
  * Auto-prepend https:// if needed

#### Description Section
- Explanation field (EditText)
  * Multiline input
  * Optional
  * Rich text support
  * Placeholder text

#### Tags Section
- Tag selection (ChipGroup)
  * Show available tags as chips
  * Allow multiple selection
  * Auto-completion for existing tags
  * Auto-suggest related tags
  * Smart tag inference:
    - Add "pure-ss" for siteswap patterns
    - Add "crossed-arms" for underarm patterns
    - Add "carry" for orbit/machine patterns
    - Add "multiplex" for stacked patterns
    - Add "body-throw" for various body throws
    - Add "ss1" for specific pattern types

#### Related Patterns Section
- Prerequisites selection
  * Show pattern suggestions based on:
    - Similar tags
    - Lower difficulty
    - Same number of balls
  * Allow multiple selection
  * Show selected patterns as chips
  * Quick removal option

- Related patterns selection
  * Show pattern suggestions based on:
    - Similar tags
    - Similar difficulty
    - Same number of balls
  * Allow multiple selection
  * Show selected patterns as chips
  * Quick removal option

- Dependent patterns selection
  * Show pattern suggestions based on:
    - Similar tags
    - Higher difficulty
    - Same number of balls
  * Allow multiple selection
  * Show selected patterns as chips
  * Quick removal option

### 3. Pattern Selection Dialog
Create a reusable dialog for selecting patterns:
- Search field with auto-complete
- Filter by tags
- Smart sorting:
  * Prerequisites: Sort by ascending difficulty
  * Dependents: Sort by descending difficulty
  * Related: Sort by tag similarity
- Show patterns in a RecyclerView
- Allow multiple selection
- Show currently selected patterns
- Quick removal option

## Implementation Details

### 1. Data Classes
```kotlin
data class CreatePatternData(
    val name: String,
    val difficulty: Int?,
    val siteswap: String?,
    val num: String?,
    val explanation: String?,
    val gifUrl: String?,
    val video: String?,
    val videoStartTime: Long?,
    val videoEndTime: Long?,
    val url: String?,
    val tags: List<String>,
    val prerequisites: List<String>,
    val dependents: List<String>,
    val related: List<String>
)

data class VideoTimeRange(
    val startTime: Long?,
    val endTime: Long?
)

sealed class FormValidationResult {
    object Valid : FormValidationResult()
    data class Invalid(val errors: Map<String, String>) : FormValidationResult()
}
```

### 2. View Model
```kotlin
class CreatePatternViewModel : ViewModel() {
    // State management
    private val _uiState = MutableStateFlow<CreatePatternUiState>()
    val uiState: StateFlow<CreatePatternUiState> = _uiState
    
    // Form data
    private val _formData = MutableStateFlow<CreatePatternData>()
    val formData: StateFlow<CreatePatternData> = _formData
    
    // Validation
    private val _validationState = MutableStateFlow<FormValidationResult>()
    val validationState: StateFlow<FormValidationResult> = _validationState
    
    // Pattern operations
    fun createPattern(data: CreatePatternData)
    fun validateInput(): FormValidationResult
    fun validateSiteswap(siteswap: String): Boolean
    fun validateVideoUrl(url: String): Boolean
    fun validateTimeFormat(time: String): Boolean
    
    // Auto-completion
    fun getPatternSuggestions(query: String): Flow<List<Pattern>>
    fun getTagSuggestions(query: String): Flow<List<String>>
    
    // Pattern suggestions
    fun getPrerequisiteSuggestions(): Flow<List<Pattern>>
    fun getDependentSuggestions(): Flow<List<Pattern>>
    fun getRelatedSuggestions(): Flow<List<Pattern>>
    
    // Smart features
    fun detectSiteswapFromName(name: String): String?
    fun inferTagsFromPattern(pattern: CreatePatternData): List<String>
    fun calculateDifficultyFromSiteswap(siteswap: String): Float?
}
```

### 3. Database Updates
- Add new methods to PatternDao for:
  * Creating new patterns
  * Getting pattern suggestions based on criteria
  * Getting all available tags
  * Checking for duplicate patterns
  * Getting patterns by difficulty range
  * Getting patterns by tag similarity

### 4. Navigation
- Add navigation action from patterns screen to create pattern bottom sheet
- Add navigation to pattern selection dialog
- Handle back navigation properly
- Save form state during navigation

## UI/UX Considerations

### 1. Form Validation
- Real-time validation as user types
- Clear error messages with suggestions
- Visual indicators for invalid fields
- Prevent submission of invalid data
- Auto-correction where possible

### 2. User Experience
- Save form state during configuration changes
- Show loading indicators during pattern creation
- Provide clear success/error feedback
- Add preview capabilities for media URLs
- Implement auto-save draft functionality
- Smooth animations for transitions
- Keyboard handling optimization
- Support for screen rotation

### 3. Pattern Suggestions
- Implement efficient filtering and sorting
- Show relevant information in suggestion items
- Allow quick selection/deselection
- Update suggestions based on current form data
- Cache suggestions for better performance

## Testing Plan

### 1. Unit Tests
- ViewModel tests
- Repository tests
- Validation logic tests
- Pattern suggestion algorithm tests
- Siteswap detection tests
- Tag inference tests

### 2. UI Tests
- Form input validation
- Pattern selection dialog
- Tag selection and inference
- Navigation flow
- Configuration change handling
- Keyboard interaction
- Error message display

### 3. Integration Tests
- Pattern creation flow
- Database operations
- Pattern suggestion integration
- Media URL handling
- Auto-completion integration

## Implementation Phases

### Phase 1: Core Form (1-2 days)
1. Create FAB in patterns screen
2. Implement basic form with required fields
3. Add pattern creation functionality
4. Basic validation

### Phase 2: Smart Features (2-3 days)
1. Implement siteswap detection
2. Add tag inference system
3. Add auto-completion
4. Implement pattern suggestions

### Phase 3: Media Handling (1-2 days)
1. Add video URL support
2. Implement time range handling
3. Add GIF preview
4. Add tutorial URL handling

### Phase 4: Polish (2-3 days)
1. Enhance form validation
2. Improve error handling
3. Add loading states
4. UI/UX improvements
5. Testing

## Future Enhancements
1. Offline draft saving
2. Pattern templates
3. Batch pattern creation
4. Pattern import from URL
5. Advanced siteswap validation
6. Pattern preview generation
7. Video thumbnail generation
8. Tag categorization
9. Pattern difficulty prediction
10. Related pattern recommendation engine