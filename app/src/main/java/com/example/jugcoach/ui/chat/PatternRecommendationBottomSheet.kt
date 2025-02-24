package com.example.jugcoach.ui.chat

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.google.android.material.slider.RangeSlider
import androidx.core.view.children
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.example.jugcoach.R
import com.example.jugcoach.databinding.BottomSheetPatternRecommendationBinding
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.chip.Chip
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject
import com.example.jugcoach.data.dao.PatternDao

@AndroidEntryPoint
class PatternRecommendationBottomSheet : BottomSheetDialogFragment() {
    private var _binding: BottomSheetPatternRecommendationBinding? = null
    private val binding get() = _binding!!
    private val viewModel: ChatViewModel by activityViewModels()
    
    @Inject
    lateinit var patternDao: PatternDao

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = BottomSheetPatternRecommendationBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupNameFilter()
        setupNumBallsChips()
        setupDifficultySlider()
        setupRecordCatches()
        setupTags()
        setupButtons()
        setupPatternButtons()
        observeState()
    }

    private var nameFilterJob: kotlinx.coroutines.Job? = null

    private fun setupNameFilter() {
        binding.patternNameFilter.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                nameFilterJob?.cancel()
                nameFilterJob = viewLifecycleOwner.lifecycleScope.launch {
                    kotlinx.coroutines.delay(300) // Add 300ms delay
                    val currentFilters = viewModel.uiState.value.patternRecommendation.filters
                    viewModel.  updatePatternFilters(currentFilters.copy(nameFilter = s?.toString() ?: ""))
                }
            }
        })
    }

    private fun setupNumBallsChips() {
        val numBalls = (1..11).map { it.toString() }
        numBalls.forEach { num ->
            val chip = Chip(requireContext()).apply {
                text = num
                isCheckable = true
            }
            binding.numBallsGroup.addView(chip)
            
            chip.setOnCheckedChangeListener { _, isChecked ->
                if (!isUpdatingUI) {
                    val currentFilters = viewModel.uiState.value.patternRecommendation.filters
                    val newNumBalls = if (isChecked) {
                        currentFilters.numBalls + num
                    } else {
                        currentFilters.numBalls - num
                    }
                    viewModel.updatePatternFilters(currentFilters.copy(numBalls = newNumBalls))
                }
            }
        }
    }

    private var isUpdatingUI = false
    
    private fun setupDifficultySlider() {
        binding.difficultySlider.apply {
            addOnSliderTouchListener(object : RangeSlider.OnSliderTouchListener {
                override fun onStartTrackingTouch(slider: RangeSlider) {
                    // Do nothing when touch starts
                }

                override fun onStopTrackingTouch(slider: RangeSlider) {
                    // Only update if not currently updating UI
                    if (!isUpdatingUI) {
                        val currentFilters = viewModel.uiState.value.patternRecommendation.filters
                        viewModel.updatePatternFilters(currentFilters.copy(
                            difficultyRange = slider.values[0]..slider.values[1]
                        ))
                    }
                }
            })
        }
    }

    private fun setupRecordCatches() {
        binding.minCatches.setOnFocusChangeListener { _, hasFocus ->
            if (!hasFocus && !isUpdatingUI) {
                val currentFilters = viewModel.uiState.value.patternRecommendation.filters
                viewModel.updatePatternFilters(currentFilters.copy(
                    minCatches = binding.minCatches.text?.toString()?.toIntOrNull()
                ))
            }
        }

        binding.maxCatches.setOnFocusChangeListener { _, hasFocus ->
            if (!hasFocus && !isUpdatingUI) {
                val currentFilters = viewModel.uiState.value.patternRecommendation.filters
                viewModel.updatePatternFilters(currentFilters.copy(
                    maxCatches = binding.maxCatches.text?.toString()?.toIntOrNull()
                ))
            }
        }
    }

    private fun setupTags() {
        viewLifecycleOwner.lifecycleScope.launch {
            // Get all patterns and extract unique tags
            val patterns = patternDao.getAllPatterns()
            val allTags = patterns.flatMap { it.tags }.distinct().sorted()
            
            allTags.forEach { tag ->
                val chip = Chip(requireContext()).apply {
                    text = tag
                    isCheckable = true
                }
                binding.tagsGroup.addView(chip)
                
                chip.setOnCheckedChangeListener { _, isChecked ->
                    if (!isUpdatingUI) {
                        val currentFilters = viewModel.uiState.value.patternRecommendation.filters
                        val newTags = if (isChecked) {
                            currentFilters.tags + tag
                        } else {
                            currentFilters.tags - tag
                        }
                        viewModel.updatePatternFilters(currentFilters.copy(tags = newTags))
                    }
                }
            }
        }
    }

    private fun setupButtons() {
        binding.refreshRecommendation.setOnClickListener {
            viewModel.getNewPatternRecommendation()
        }
    }

    private fun observeState() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.uiState.collect { state ->
                Log.d("PatternDebug", "State update received in bottom sheet")
                val recommendationState = state.patternRecommendation
                
                // Update recommended pattern display
                recommendationState.recommendedPattern?.let { pattern ->
                    binding.recommendedPatternName.text = pattern.name
                    binding.startPatternButton.isEnabled = true
                } ?: run {
                    binding.recommendedPatternName.text = getString(R.string.no_pattern_found)
                    binding.startPatternButton.isEnabled = false
                }

                // Update filter UI to match current state
                recommendationState.filters.let { filters ->
                    // Update num balls chips
                    binding.numBallsGroup.children.filterIsInstance<Chip>().forEach { chip ->
                        val num = chip.text.toString()
                        chip.isChecked = filters.numBalls.contains(num)
                    }

                    // Update difficulty slider without triggering listener
                    isUpdatingUI = true
                    binding.difficultySlider.values = listOf(
                        filters.difficultyRange.start,
                        filters.difficultyRange.endInclusive
                    )
                    isUpdatingUI = false

                    // Update record catches inputs
                    if (!binding.minCatches.hasFocus()) {
                        binding.minCatches.setText(filters.minCatches?.toString() ?: "")
                    }
                    if (!binding.maxCatches.hasFocus()) {
                        binding.maxCatches.setText(filters.maxCatches?.toString() ?: "")
                    }

                    // Update name filter
                    if (!binding.patternNameFilter.hasFocus()) {
                        binding.patternNameFilter.setText(filters.nameFilter)
                    }

                    // Update tags
                    binding.tagsGroup.children.filterIsInstance<Chip>().forEach { chip ->
                        chip.isChecked = filters.tags.contains(chip.text.toString())
                    }
                }

                // Update selected pattern section
                recommendationState.selectedPattern?.let { pattern ->
                    binding.selectedPatternSection.visibility = View.VISIBLE
                    binding.selectedPatternName.text = pattern.name
                } ?: run {
                    binding.selectedPatternSection.visibility = View.GONE
                }
            }
        }
    }

    private fun setupPatternButtons() {
        binding.patternInfoButton.setOnClickListener {
            viewModel.uiState.value.patternRecommendation.recommendedPattern?.let { pattern ->
                findNavController().navigate(
                    R.id.action_nav_chat_to_patternDetailsFragment,
                    Bundle().apply {
                        putString("patternId", pattern.id)
                    }
                )
            }
        }

        binding.startPatternButton.setOnClickListener {
            viewModel.uiState.value.patternRecommendation.recommendedPattern?.let { pattern ->
                viewModel.startPatternRun(pattern)
                dismiss()
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    override fun dismiss() {
        super.dismiss()
        viewModel.hidePatternRecommendation()
    }

    companion object {
        const val TAG = "PatternRecommendationBottomSheet"
        
        fun newInstance() = PatternRecommendationBottomSheet()
    }
}