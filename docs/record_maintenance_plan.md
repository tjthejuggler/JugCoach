# Record Maintenance Implementation Plan

## Current Situation
- Pattern entities have an optional `Record` field storing the all-time best catch count
- Currently only comparing new runs against existing record
- Potential for missed records if historical runs have higher catches

## Implementation Plan

### 1. Database Migration
Create a new database migration that:
- Scans all patterns
- For each pattern, checks all runs to find the highest catch count
- Updates the record field if necessary

### 2. Run Modification Handling

#### Adding Runs (Current)
```kotlin
// In PatternDao
suspend fun addRun(patternId: String, catches: Int?, duration: Long?, cleanEnd: Boolean, date: Long) {
    val pattern = getPatternById(patternId) ?: return
    // ...
    updatePattern(pattern.copy(
        record = if (catches != null && (pattern.record == null || catches > pattern.record.catches)) {
            Record(catches = catches, date = date)
        } else pattern.record
    ))
}
```

#### Editing Runs (To Add)
```kotlin
// In PatternDao
suspend fun updateRun(patternId: String, oldRun: Run, newRun: Run) {
    val pattern = getPatternById(patternId) ?: return
    
    // Update the run in history
    val updatedRuns = pattern.runHistory.runs.map { 
        if (it.date == oldRun.date) newRun else it 
    }
    
    // Find highest catch count among all runs
    val maxCatch = updatedRuns.mapNotNull { it.catches }.maxOrNull()
    val maxCatchRun = maxCatch?.let { max ->
        updatedRuns.find { it.catches == max }
    }
    
    // Update pattern with new history and potentially new record
    updatePattern(pattern.copy(
        runHistory = pattern.runHistory.copy(runs = updatedRuns),
        record = maxCatchRun?.let { run ->
            Record(catches = run.catches!!, date = run.date)
        }
    ))
}
```

#### Deleting Runs (To Add)
```kotlin
// In PatternDao
suspend fun deleteRun(patternId: String, runToDelete: Run) {
    val pattern = getPatternById(patternId) ?: return
    
    // Remove run from history
    val updatedRuns = pattern.runHistory.runs.filter { it.date != runToDelete.date }
    
    // Find new highest catch count
    val maxCatch = updatedRuns.mapNotNull { it.catches }.maxOrNull()
    val maxCatchRun = maxCatch?.let { max ->
        updatedRuns.find { it.catches == max }
    }
    
    // Update pattern with new history and potentially new record
    updatePattern(pattern.copy(
        runHistory = pattern.runHistory.copy(runs = updatedRuns),
        record = maxCatchRun?.let { run ->
            Record(catches = run.catches!!, date = run.date)
        }
    ))
}
```

### 3. Unit Tests

Create tests in `PatternDaoTest` to verify:

1. Record Updates
```kotlin 
@Test
fun whenAddingRunWithHigherCatches_shouldUpdateRecord()
@Test
fun whenAddingRunWithLowerCatches_shouldNotUpdateRecord()
@Test
fun whenEditingRunThatWasRecord_shouldUpdateRecordToNextHighest()
@Test
fun whenDeletingRecordRun_shouldUpdateRecordToNextHighest()
```

2. Edge Cases
```kotlin
@Test
fun whenPatternHasNoRuns_recordShouldBeNull()
@Test
fun whenAllRunsDeleted_recordShouldBeNull()
@Test
fun whenMultipleRunsHaveSameHighestCatches_shouldUseEarliest()
```

## Benefits

1. Maintains O(1) lookup performance for displaying records
2. Ensures record accuracy across all run modifications
3. No performance impact from scanning all runs each time
4. Proper handling of edge cases

## Migration Steps

1. Create database migration script
2. Implement new run modification methods
3. Add unit tests
4. Update UI to use new methods
5. Test with large datasets for performance validation