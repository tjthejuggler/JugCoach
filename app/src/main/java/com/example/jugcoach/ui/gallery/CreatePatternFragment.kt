package com.example.jugcoach.ui.gallery

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.children
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import com.example.jugcoach.R
import com.example.jugcoach.data.dao.PatternDao
import com.example.jugcoach.data.entity.Pattern
import com.example.jugcoach.databinding.FragmentCreatePatternBinding
import com.google.android.material.chip.Chip
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class CreatePatternFragment : Fragment() {
    private var _binding: FragmentCreatePatternBinding? = null
    private val binding get() = _binding!!
    private val viewModel: CreatePatternViewModel by viewModels()
    private val args: CreatePatternFragmentArgs by navArgs()
    
    @Inject
    lateinit var patternDao: PatternDao

    private suspend fun getPatternById(patternId: String): Pattern? {
        return patternDao.getPatternById(patternId)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentCreatePatternBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupViews()
        observeViewModel()

        // Initialize from source pattern if provided
        args.sourcePatternId?.let { patternId ->
            args.relationshipType?.let { relationship ->
                viewModel.initializeFromSourcePattern(patternId, relationship)
            }
        }
    }

    private fun setupViews() {
        binding.apply {
            // Setup number of balls chips
            (1..11).forEach { num ->
                numBallsGroup.addView(
                    Chip(requireContext()).apply {
                        text = num.toString()
                        isCheckable = true
                        setOnCheckedChangeListener { _, isChecked ->
                            if (isChecked) {
                                viewModel.updateNumBalls(num.toString())
                            }
                        }
                    }
                )
            }

            // Setup input field listeners
            nameInput.doAfterTextChanged { text ->
                viewModel.updateName(text?.toString() ?: "")
            }

            difficultyInput.doAfterTextChanged { text ->
                viewModel.updateDifficulty(text?.toString() ?: "")
            }

            siteswapInput.doAfterTextChanged { text ->
                viewModel.updateSiteswap(text?.toString() ?: "")
            }

            videoInput.doAfterTextChanged { text ->
                viewModel.updateVideoUrl(text?.toString() ?: "")
            }

            gifUrlInput.doAfterTextChanged { text ->
                viewModel.updateGifUrl(text?.toString() ?: "")
            }

            tutorialUrlInput.doAfterTextChanged { text ->
                viewModel.updateTutorialUrl(text?.toString() ?: "")
            }

            explanationInput.doAfterTextChanged { text ->
                viewModel.updateExplanation(text?.toString() ?: "")
            }

            // Setup video time range toggle
            toggleTimeButton.setOnClickListener {
                val isVisible = videoTimeLayout.visibility == View.VISIBLE
                videoTimeLayout.visibility = if (isVisible) View.GONE else View.VISIBLE
                toggleTimeButton.text = getString(
                    if (isVisible) R.string.add_time_range else R.string.remove_time_range
                )
            }

            startTimeInput.doAfterTextChanged { text ->
                viewModel.updateVideoStartTime(text?.toString() ?: "")
            }

            endTimeInput.doAfterTextChanged { text ->
                viewModel.updateVideoEndTime(text?.toString() ?: "")
            }

            // Setup pattern relation buttons
            addPrereqsButton.setOnClickListener {
                showPatternSelectionDialog(CreatePatternViewModel.RELATIONSHIP_PREREQUISITE)
            }

            addRelatedButton.setOnClickListener {
                showPatternSelectionDialog(CreatePatternViewModel.RELATIONSHIP_RELATED)
            }

            addDependentsButton.setOnClickListener {
                showPatternSelectionDialog(CreatePatternViewModel.RELATIONSHIP_DEPENDENT)
            }

            // Setup action buttons
            saveButton.setOnClickListener {
                viewModel.savePattern()
            }

            cancelButton.setOnClickListener {
                findNavController().navigateUp()
            }
        }
    }

    private fun observeViewModel() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.uiState.collect { state ->
                updateUi(state)
            }
        }
    }

    private fun updateUi(state: CreatePatternUiState) {
        binding.apply {
            // Update form fields
            nameLayout.error = state.nameError
            difficultyLayout.error = state.difficultyError
            siteswapLayout.error = state.siteswapError
            videoLayout.error = state.videoUrlError
            gifUrlLayout.error = state.gifUrlError
            tutorialUrlLayout.error = state.tutorialUrlError

            // Update name if not focused (to prevent cursor jumping)
            if (!nameInput.isFocused && nameInput.text?.toString() != state.name) {
                nameInput.setText(state.name)
            }

            // Update difficulty if not focused
            if (!difficultyInput.isFocused && difficultyInput.text?.toString() != state.difficulty) {
                difficultyInput.setText(state.difficulty)
            }

            // Update number of balls selection
            if (state.numBalls.isNotBlank()) {
                val numBalls = state.numBalls.toIntOrNull()
                numBalls?.let { num ->
                    for (view in numBallsGroup.children) {
                        if (view is Chip) {
                            view.isChecked = view.text.toString() == num.toString()
                        }
                    }
                }
            }

            // Update tags
            availableTagsGroup.removeAllViews()
            state.availableTags.forEach { tag ->
                if (!state.tags.contains(tag)) {
                    availableTagsGroup.addView(
                        Chip(requireContext()).apply {
                            text = tag
                            isCheckable = false
                            setOnClickListener {
                                viewModel.addTag(tag)
                            }
                        }
                    )
                }
            }

            selectedTagsGroup.removeAllViews()
            state.tags.forEach { tag ->
                selectedTagsGroup.addView(
                    Chip(requireContext()).apply {
                        text = tag
                        isCloseIconVisible = true
                        setOnCloseIconClickListener {
                            viewModel.removeTag(tag)
                        }
                    }
                )
            }

            // Update pattern relations
            updatePatternRelationChips(prereqsGroup, state.prerequisites) { viewModel.removePrerequisite(it) }
            updatePatternRelationChips(relatedGroup, state.relatedPatterns) { viewModel.removeRelatedPattern(it) }
            updatePatternRelationChips(dependentsGroup, state.dependentPatterns) { viewModel.removeDependentPattern(it) }

            // Handle save completion
            if (state.isSaved) {
                findNavController().navigateUp()
            }
        }
    }

    private fun updatePatternRelationChips(
        chipGroup: com.google.android.material.chip.ChipGroup,
        patterns: Set<String>,
        onRemove: (String) -> Unit
    ) {
        chipGroup.removeAllViews()
        viewLifecycleOwner.lifecycleScope.launch {
            patterns.forEach { patternId ->
                patternDao.getPatternById(patternId)?.let { pattern ->
                    chipGroup.addView(
                        Chip(requireContext()).apply {
                            text = pattern.name
                            isCloseIconVisible = true
                            setOnCloseIconClickListener {
                                onRemove(patternId)
                            }
                        }
                    )
                }
            }
        }
    }

    private fun showPatternSelectionDialog(relationshipType: String) {
        val selectionType = when (relationshipType) {
            CreatePatternViewModel.RELATIONSHIP_PREREQUISITE -> PatternSelectionDialog.SelectionType.PREREQUISITES
            CreatePatternViewModel.RELATIONSHIP_RELATED -> PatternSelectionDialog.SelectionType.RELATED
            CreatePatternViewModel.RELATIONSHIP_DEPENDENT -> PatternSelectionDialog.SelectionType.DEPENDENTS
            else -> return
        }
        
        val dialog = PatternSelectionDialog.newInstance(selectionType)
        dialog.setSelectionListener(object : PatternSelectionDialog.PatternSelectionListener {
            override fun onPatternsSelected(patterns: List<Pattern>) {
                patterns.forEach { pattern ->
                    when (relationshipType) {
                        CreatePatternViewModel.RELATIONSHIP_PREREQUISITE -> viewModel.addPrerequisite(pattern.id)
                        CreatePatternViewModel.RELATIONSHIP_RELATED -> viewModel.addRelatedPattern(pattern.id)
                        CreatePatternViewModel.RELATIONSHIP_DEPENDENT -> viewModel.addDependentPattern(pattern.id)
                    }
                }
            }
        })
        dialog.show(childFragmentManager, PatternSelectionDialog.TAG)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}