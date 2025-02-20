# Catches Per Minute Implementation Plan

## 1. Data Model Updates

### Run Data Class Changes
```kotlin
data class Run(
    val catches: Int? = null,
    val duration: Long? = null, // duration in seconds
    val catchesPerMinute: Double? = null, // new field
    val isCleanEnd: Boolean,
    val date: Long
)
```

### Utility Function
Add to a new Utils file:
```kotlin
object RunUtils {
    fun calculateCatchesPerMinute(catches: Int?, durationSeconds: Long?): Double? {
        if (catches == null || durationSeconds == null || durationSeconds == 0L) {
            return null
        }
        return ((catches * 60.0) / durationSeconds).round(2)
    }
    
    private fun Double.round(decimals: Int): Double {
        var multiplier = 1.0
        repeat(decimals) { multiplier *= 10 }
        return kotlin.math.round(this * multiplier) / multiplier
    }
}
```

## 2. Implementation Points

### A. Pattern Details Page
1. Update the pattern detail layout to show catches per minute:
```xml
<!-- Add below the record display -->
<TextView
    android:id="@+id/record_catches_per_minute"
    android:layout_width="wrap_content"
    android:layout_height="wrap_content"
    tools:text="Catches/min: 120.50"
    android:visibility="gone" />
```

2. Update the view logic to calculate and display catches per minute:
```kotlin
// In pattern details view model or fragment
val catchesPerMinute = RunUtils.calculateCatchesPerMinute(catches, duration)
recordCatchesPerMinuteView.apply {
    if (catchesPerMinute != null) {
        visibility = View.VISIBLE
        text = getString(R.string.catches_per_minute_format, catchesPerMinute)
    } else {
        visibility = View.GONE
    }
}
```

### B. Timer Dialog Updates

1. Update dialog_end_run.xml to include duration field:
```xml
<com.google.android.material.textfield.TextInputLayout
    android:layout_width="match_parent"
    android:layout_height="wrap_content"
    android:hint="@string/enter_duration_seconds"
    style="@style/Widget.MaterialComponents.TextInputLayout.OutlinedBox">

    <com.google.android.material.textfield.TextInputEditText
        android:id="@+id/duration_input"
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:inputType="number"/>

</com.google.android.material.textfield.TextInputLayout>
```

2. Update the run creation logic:
```kotlin
val catches = catchesInput.text.toString().toIntOrNull()
val duration = durationInput.text.toString().toLongOrNull()
val catchesPerMinute = RunUtils.calculateCatchesPerMinute(catches, duration)

val run = Run(
    catches = catches,
    duration = duration,
    catchesPerMinute = catchesPerMinute,
    isCleanEnd = true,
    date = System.currentTimeMillis()
)
```

## 3. String Resources
Add to strings.xml:
```xml
<string name="enter_duration_seconds">Duration (seconds)</string>
<string name="catches_per_minute_format">Catches/min: %.2f</string>
```

## 4. Testing Plan

1. Test RunUtils.calculateCatchesPerMinute:
   - Test calculation accuracy (e.g., 50 catches in 30 seconds = 100.00)
   - Test rounding to 2 decimal places
   - Test null handling
   - Test zero duration handling

2. Test Run Creation:
   - Verify calculation happens on new run creation
   - Verify run is saved with all fields properly populated

3. Test UI:
   - Verify catches/min visibility logic
   - Verify format displays 2 decimal places
   - Verify field is hidden when calculation isn't possible

## Next Steps

1. Begin with the data model changes to Run class
2. Implement the RunUtils class with the calculation function
3. Update the UI components to handle the new field
4. Add unit tests for the calculation logic
5. Manual testing of the complete feature

## Note
- No database migration needed as existing data doesn't have both catches and duration
- UI will hide the catches per minute field when it can't be calculated
- All displayed values will be rounded to 2 decimal places