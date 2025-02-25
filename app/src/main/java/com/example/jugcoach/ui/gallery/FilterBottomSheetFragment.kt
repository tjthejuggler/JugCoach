package com.example.jugcoach.ui.gallery

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.children
import androidx.core.widget.doAfterTextChanged
import com.example.jugcoach.R
import com.example.jugcoach.databinding.BottomSheetFiltersBinding
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.chip.Chip
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class FilterBottomSheetFragment : BottomSheetDialogFragment() {
    private var _binding: BottomSheetFiltersBinding? = null
    private val binding get() = _binding!!
private var filterListener: FilterListener? = null
private var isInitialSetup = true
private lateinit var currentFilters: FilterOptions
private lateinit var currentSortOrder: SortOrder

interface FilterListener {
    fun onFiltersApplied(filters: FilterOptions, sortOrder: SortOrder)
    fun getCurrentFilters(): FilterOptions
    fun getCurrentSortOrder(): SortOrder
}

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = BottomSheetFiltersBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        // Get current filters and sort order
        currentFilters = filterListener?.getCurrentFilters() ?: FilterOptions()
        currentSortOrder = filterListener?.getCurrentSortOrder() ?: SortOrder.NAME_ASC
        
        Log.d(DEBUG_TAG, "Opening filter sheet with filters: $currentFilters")
        
        // Set up UI components
        setupNumBallsChips()
        setupDifficultySlider()
        setupPracticedPeriodToggle()
        
        // Show either filters or sort options based on argument
        val showSortTab = arguments?.getBoolean("show_sort_tab", false) ?: false

        // Show/hide sections based on selected tab
        binding.apply {
            filtersSection.visibility = if (!showSortTab) View.VISIBLE else View.GONE
            sortSectionTitle.visibility = if (showSortTab) View.VISIBLE else View.GONE
            sortOrderGroup.visibility = if (showSortTab) View.VISIBLE else View.GONE
        }

        // Set up all UI components first
        setupSortOrderGroup()
        setupFilterListeners()
        
        // Restore filter state
        restoreFilterState()
        
        // Apply any pending tags
        pendingTags?.let { tags ->
            setAvailableTags(tags)
            pendingTags = null
        }
        
        Log.d(DEBUG_TAG, "Initial setup complete, current filters: $currentFilters")
        isInitialSetup = false // Mark setup as complete after all initialization
    }

    private fun setupSortOrderGroup() {
        // Set initial selection without triggering listener
        arguments?.getString("current_sort_order")?.let {
            try {
                val currentSortOrder = SortOrder.valueOf(it)
                val buttonId = when (currentSortOrder) {
                    SortOrder.SEARCH_RELEVANCE -> R.id.sort_relevance
                    SortOrder.NAME_ASC -> R.id.sort_name_asc
                    SortOrder.NAME_DESC -> R.id.sort_name_desc
                    SortOrder.DIFFICULTY_ASC -> R.id.sort_difficulty_asc
                    SortOrder.DIFFICULTY_DESC -> R.id.sort_difficulty_desc
                    SortOrder.CATCHES_ASC -> R.id.sort_catches_asc
                    SortOrder.CATCHES_DESC -> R.id.sort_catches_desc
                    SortOrder.LAST_PRACTICED_ASC -> R.id.sort_last_practiced_asc
                    SortOrder.LAST_PRACTICED_DESC -> R.id.sort_last_practiced_desc
                }
                binding.sortOrderGroup.check(buttonId)
            } catch (e: IllegalArgumentException) { }
        }

        // Set up the listener for user interactions
        binding.sortOrderGroup.setOnCheckedChangeListener { _, checkedId ->
            if (!isInitialSetup) {
                val sortOrder = when (checkedId) {
                    R.id.sort_relevance -> SortOrder.SEARCH_RELEVANCE
                    R.id.sort_name_asc -> SortOrder.NAME_ASC
                    R.id.sort_name_desc -> SortOrder.NAME_DESC
                    R.id.sort_difficulty_asc -> SortOrder.DIFFICULTY_ASC
                    R.id.sort_difficulty_desc -> SortOrder.DIFFICULTY_DESC
                    R.id.sort_catches_asc -> SortOrder.CATCHES_ASC
                    R.id.sort_catches_desc -> SortOrder.CATCHES_DESC
                    R.id.sort_last_practiced_asc -> SortOrder.LAST_PRACTICED_ASC
                    R.id.sort_last_practiced_desc -> SortOrder.LAST_PRACTICED_DESC
                    else -> return@setOnCheckedChangeListener
                }
                currentSortOrder = sortOrder
                filterListener?.onFiltersApplied(currentFilters, currentSortOrder)
            }
        }
    }

    private fun restoreFilterState() {
        Log.d(DEBUG_TAG, "Restoring filter state with numBalls: ${currentFilters.numBalls}")
        
        // Restore number of balls selection
        binding.numBallsGroup.children.forEach { view ->
            if (view is Chip) {
                val shouldBeChecked = view.text.toString() in currentFilters.numBalls
                Log.d(DEBUG_TAG, "Chip ${view.id} (${view.text}) should be checked: $shouldBeChecked")
                view.isChecked = shouldBeChecked
            }
        }

        // Restore difficulty range
        binding.difficultySlider.setValues(
            currentFilters.difficultyRange.first,
            currentFilters.difficultyRange.second
        )

        // Restore practiced within values
        currentFilters.practicedWithin?.let { practiced ->
            binding.practicedValue.setText(practiced.value.toString())
            val buttonId = when (practiced.period) {
                Period.DAYS -> R.id.period_days
                Period.WEEKS -> R.id.period_weeks
                Period.MONTHS -> R.id.period_months
            }
            binding.practicedPeriodToggle.check(buttonId)
        }

        // Restore catches range
        binding.minCatches.setText(currentFilters.catchesRange.first?.toString() ?: "")
        binding.maxCatches.setText(currentFilters.catchesRange.second?.toString() ?: "")
    }

    private val chipIds = mutableMapOf<String, Int>()

    private fun setupNumBallsChips() {
        Log.d(DEBUG_TAG, "Setting up num balls chips with current filters: $currentFilters")
        binding.numBallsGroup.removeAllViews() // Clear any existing chips
        chipIds.clear() // Clear stored IDs
        
        (1..11).forEach { num ->
            Log.d(DEBUG_TAG, "Creating chip for $num balls")
            val chip = Chip(context).apply {
                id = View.generateViewId() // Ensure unique ID
                text = num.toString()
                isCheckable = true
                isCheckedIconVisible = true
                isCloseIconVisible = false
                // Set initial checked state based on current filters
                isChecked = num.toString() in currentFilters.numBalls
                Log.d(DEBUG_TAG, "Chip ${num} initial checked state: $isChecked")
                setOnCheckedChangeListener { buttonView, isChecked ->
                    Log.d(DEBUG_TAG, "Chip ${buttonView.id} (${num} balls) checked: $isChecked")
                    if (!isInitialSetup) {
                        Log.d(DEBUG_TAG, "Triggering filter update from chip listener")
                        // Update current filters immediately
                        currentFilters = currentFilters.copy(
                            numBalls = getSelectedBalls()
                        )
                        Log.d(DEBUG_TAG, "Updated current filters to: $currentFilters")
                        collectAndApplyFilters()
                    }
                }
            }
            // Store the ID mapping
            chipIds[num.toString()] = chip.id
            Log.d(DEBUG_TAG, "Adding chip for ${num} balls with id: ${chip.id}")
            binding.numBallsGroup.addView(chip)
        }
    }

    private fun getSelectedBalls(): Set<String> {
        return binding.numBallsGroup.checkedChipIds.mapNotNull { id ->
            binding.numBallsGroup.findViewById<Chip>(id)?.text?.toString()
        }.toSet().also {
            Log.d(DEBUG_TAG, "Currently selected balls: $it")
        }
    }

    private fun setupDifficultySlider() {
        binding.difficultySlider.apply {
            setValues(1f, 10f)
        }
    }

    private fun setupPracticedPeriodToggle() {
        binding.practicedPeriodToggle.apply {
            check(R.id.period_days)
        }
    }

    private fun collectAndApplyFilters() {
        Log.d(DEBUG_TAG, "Collecting filters...")
        
        // Get selected balls using our helper
        val selectedBalls = getSelectedBalls()
        Log.d(DEBUG_TAG, "Selected balls: $selectedBalls")
        
        // Create new filters with selected balls
        val newFilters = FilterOptions(
            numBalls = selectedBalls,
            difficultyRange = binding.difficultySlider.values.let {
                Pair(it[0], it[1])
            },
            tags = binding.selectedTagsGroup.checkedChipIds.mapNotNull { id ->
                (binding.selectedTagsGroup.findViewById<Chip>(id))?.text?.toString()
            }.toSet(),
            practicedWithin = binding.practicedValue.text?.toString()?.toIntOrNull()?.let { value ->
                val period = when (binding.practicedPeriodToggle.checkedButtonId) {
                    R.id.period_days -> Period.DAYS
                    R.id.period_weeks -> Period.WEEKS
                    R.id.period_months -> Period.MONTHS
                    else -> Period.DAYS
                }
                PracticedWithin(value, period)
            },
            catchesRange = Pair(
                binding.minCatches.text?.toString()?.toIntOrNull(),
                binding.maxCatches.text?.toString()?.toIntOrNull()
            )
        )

        Log.d(DEBUG_TAG, "New filters collected: $newFilters")
        currentFilters = newFilters

        // Update current sort order
        val newSortOrder = when (binding.sortOrderGroup.checkedRadioButtonId) {
            R.id.sort_relevance -> SortOrder.SEARCH_RELEVANCE
            R.id.sort_name_asc -> SortOrder.NAME_ASC
            R.id.sort_name_desc -> SortOrder.NAME_DESC
            R.id.sort_difficulty_asc -> SortOrder.DIFFICULTY_ASC
            R.id.sort_difficulty_desc -> SortOrder.DIFFICULTY_DESC
            R.id.sort_catches_asc -> SortOrder.CATCHES_ASC
            R.id.sort_catches_desc -> SortOrder.CATCHES_DESC
            R.id.sort_last_practiced_asc -> SortOrder.LAST_PRACTICED_ASC
            R.id.sort_last_practiced_desc -> SortOrder.LAST_PRACTICED_DESC
            else -> SortOrder.NAME_ASC
        }
        Log.d(DEBUG_TAG, "New sort order: $newSortOrder")
        currentSortOrder = newSortOrder

        Log.d(DEBUG_TAG, "Applying filters to listener")
        filterListener?.onFiltersApplied(currentFilters, currentSortOrder)
    }

    private fun setupFilterListeners() {
        // Difficulty slider listener
        binding.difficultySlider.addOnChangeListener { _, _, fromUser ->
            if (!isInitialSetup && fromUser) collectAndApplyFilters()
        }

        // Tags group listener
        binding.selectedTagsGroup.setOnCheckedChangeListener { _, _ ->
            if (!isInitialSetup) collectAndApplyFilters()
        }

        // Practiced value listener
        binding.practicedValue.doAfterTextChanged {
            if (!isInitialSetup) collectAndApplyFilters()
        }

        // Practiced period toggle listener
        binding.practicedPeriodToggle.addOnButtonCheckedListener { _, _, _ ->
            if (!isInitialSetup) collectAndApplyFilters()
        }

        // Min catches listener
        binding.minCatches.doAfterTextChanged {
            if (!isInitialSetup) collectAndApplyFilters()
        }

        // Max catches listener
        binding.maxCatches.doAfterTextChanged {
            if (!isInitialSetup) collectAndApplyFilters()
        }
    }

    fun setFilterListener(listener: FilterListener) {
        filterListener = listener
    }

    private var pendingTags: Set<String>? = null

    fun setAvailableTags(tags: Set<String>) {
        if (view == null) {
            // Store tags to be applied once view is created
            pendingTags = tags
            return
        }
        binding.selectedTagsGroup.removeAllViews()
        tags.forEach { tag ->
            val chip = Chip(context).apply {
                text = tag
                isCheckable = true
            }
            binding.selectedTagsGroup.addView(chip)
        }
    }

    override fun onDestroyView() {
        Log.d(DEBUG_TAG, "Bottom sheet being destroyed with filters: $currentFilters")
        super.onDestroyView()
        _binding = null
    }

    override fun onDismiss(dialog: android.content.DialogInterface) {
        Log.d(DEBUG_TAG, "Bottom sheet being dismissed with filters: $currentFilters")
        // Ensure filters are applied when dismissing
        filterListener?.onFiltersApplied(currentFilters, currentSortOrder)
        super.onDismiss(dialog)
    }

    companion object {
        const val TAG = "FilterBottomSheetFragment"
        private const val DEBUG_TAG = "FilterDebug"
    }
}
