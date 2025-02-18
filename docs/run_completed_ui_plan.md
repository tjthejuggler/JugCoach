# Run Completed UI Enhancement Plan

## Overview
Add new functionality to the run completed UI to allow users to:
- Practice a different pattern (randomly selected from relationships)
- Create a new pattern with pre-filled relationship data

## UI Changes

### Layout Modifications (item_pattern_run.xml)
1. Add horizontal button container at bottom
2. Move "again" button to left side
3. Add "different" and "create" buttons
4. Create popup menu layout for relationship options:
   - Prerequisites
   - Related Patterns
   - Dependent Patterns

### Visual Behavior
- Only show relationship options if patterns exist in that category
- Highlight active relationships for the current pattern
- Use standard Material Design popup menu styling

## Data Layer Changes

### PatternDao Extensions
Add methods to support:
```kotlin
// Get random pattern from relationship list
suspend fun getRandomPrerequisite(patternId: String): Pattern?
suspend fun getRandomRelated(patternId: String): Pattern?
suspend fun getRandomDependent(patternId: String): Pattern?

// Check if relationships exist
suspend fun hasPrerequisites(patternId: String): Boolean
suspend fun hasRelated(patternId: String): Boolean
suspend fun hasDependents(patternId: String): Boolean
```

## UI Logic Implementation

### PatternRunView Updates
1. Add click handlers for new buttons
2. Implement popup menu display
3. Check pattern relationships to show/hide options
4. Handle pattern selection and creation

### CreatePatternBottomSheetFragment Updates
1. Add ability to accept pre-filled data:
   - Pattern tags
   - Relationship type and target pattern
2. Update UI to show pre-filled state
3. Modify save logic to handle relationships

## Implementation Steps

1. Create new layouts
2. Implement DAO methods
3. Update PatternRunView
4. Modify CreatePatternBottomSheetFragment
5. Test all relationships and creation flows

## Testing Scenarios

1. Basic Navigation
   - Click "again" button
   - Click "different" button
   - Click "create" button

2. Relationship Testing
   - Pattern with no relationships
   - Pattern with some relationships
   - Pattern with all relationships

3. Pattern Creation
   - Create from prerequisites
   - Create from related
   - Create from dependents

4. Edge Cases
   - No valid patterns in relationship
   - Multiple patterns in relationship
   - Circular relationships