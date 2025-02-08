package com.example.jugcoach.ui.gallery

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.example.jugcoach.R
import com.example.jugcoach.databinding.BottomSheetFiltersBinding
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.chip.Chip

class FilterBottomSheetFragment : BottomSheetDialogFragment() {
    private var _binding: BottomSheetFiltersBinding? = null
    private val binding get() = _binding!!

    private var filterListener: FilterListener? = null
    private var isInitialSetup = true

    interface FilterListener {
        fun onFiltersApplied(filters: FilterOptions, sortOrder: SortOrder)
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
            applyFilters.visibility = if (!showSortTab) View.VISIBLE else View.GONE
        }

        setupSortOrderGroup()
        setupApplyButton()
        
        // Apply any pending tags
        pendingTags?.let { tags ->
            setAvailableTags(tags)
            pendingTags = null
        }
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
                    else -> return@setOnCheckedChangeListener
                }
                filterListener?.onFiltersApplied(FilterOptions(), sortOrder)
                dismiss()
            }
        }

        // Mark initial setup as complete
        isInitialSetup = false
    }

    private fun setupNumBallsChips() {
        (1..11).forEach { num ->
            val chip = Chip(context).apply {
                text = num.toString()
                isCheckable = true
            }
            binding.numBallsGroup.addView(chip)
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

    private fun setupApplyButton() {
        binding.applyFilters.setOnClickListener {
            val filters = FilterOptions(
                numBalls = binding.numBallsGroup.checkedChipIds.mapNotNull { id ->
                    (binding.numBallsGroup.findViewById<Chip>(id))?.text?.toString()
                }.toSet(),
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

            // Get selected sort order
            val sortOrder = when (binding.sortOrderGroup.checkedRadioButtonId) {
                R.id.sort_relevance -> SortOrder.SEARCH_RELEVANCE
                R.id.sort_name_asc -> SortOrder.NAME_ASC
                R.id.sort_name_desc -> SortOrder.NAME_DESC
                R.id.sort_difficulty_asc -> SortOrder.DIFFICULTY_ASC
                R.id.sort_difficulty_desc -> SortOrder.DIFFICULTY_DESC
                R.id.sort_catches_asc -> SortOrder.CATCHES_ASC
                R.id.sort_catches_desc -> SortOrder.CATCHES_DESC
                else -> SortOrder.NAME_ASC
            }

            filterListener?.onFiltersApplied(filters, sortOrder)
            dismiss()
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
        super.onDestroyView()
        _binding = null
    }

    companion object {
        const val TAG = "FilterBottomSheetFragment"
    }
}
