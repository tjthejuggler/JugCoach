# Pattern Relationship Creation Plan

## Overview
When a user completes a run and clicks the 'create' button, they should be presented with three options to create a new pattern that has a specific relationship with the current pattern:
- Prerequisite (difficulty = current - 1)
- Related (same difficulty)
- Dependent (difficulty = current + 1)

## Implementation Steps

### 1. Create Menu Resource
Create a new menu resource `pattern_relationship_menu.xml` with three items:
- Prerequisite pattern
- Related pattern
- Dependent pattern

### 2. Update Chat Message Item
Modify the create button in `item_chat_message.xml` to show the popup menu when clicked:
- Keep the existing button
- Add click handler to show popup menu
- Pass selected relationship type to callback

### 3. Update View Model and Navigation
The CreatePatternViewModel already has the necessary functionality:
- Uses constants for relationship types (RELATIONSHIP_PREREQUISITE, RELATIONSHIP_RELATED, RELATIONSHIP_DEPENDENT)
- Has initializeFromSourcePattern() method that:
  * Sets up proper relationships
  * Adjusts difficulty based on relationship type
  * Copies relevant pattern data (tags, ball count for related patterns)

### 4. Navigation Updates
Update navigation to include:
- Source pattern ID
- Relationship type
- Pass these through to CreatePatternViewModel

## Technical Details

### Menu Resource
```xml
<menu>
    <item
        android:id="@+id/create_prerequisite"
        android:title="Create Prerequisite Pattern" />
    <item
        android:id="@+id/create_related"
        android:title="Create Related Pattern" />
    <item
        android:id="@+id/create_dependent"
        android:title="Create Dependent Pattern" />
</menu>
```

### Navigation Arguments
Add to pattern creation destination:
```xml
<argument
    android:name="sourcePatternId"
    android:defaultValue="@null"
    app:argType="string"
    app:nullable="true" />
<argument
    android:name="relationshipType"
    android:defaultValue="@null"
    app:argType="string"
    app:nullable="true" />
```

### Behavior Details
1. When 'create' is clicked on a run completion message:
   - Show popup menu with three options
   - Each option navigates to pattern creation
   - Passes source pattern ID and relationship type

2. In pattern creation:
   - If source pattern ID and relationship type are provided:
     * Initialize form with source pattern data
     * Set up proper relationship
     * Adjust difficulty based on relationship type
     * Copy ball count for related patterns
     * Copy tags from source pattern
     * Include source pattern's related patterns in the same section

## UI/UX Considerations
- Menu items should be clearly labeled to indicate the relationship type
- Consider adding brief descriptions or tooltips to explain each relationship type
- Ensure the popup menu appears in a natural position relative to the create button
- Maintain consistent styling with the rest of the application

## Testing Considerations
- Test all three relationship types
- Verify proper difficulty adjustment
- Verify proper relationship setup
- Verify proper data copying (tags, ball count)
- Test navigation with and without source pattern