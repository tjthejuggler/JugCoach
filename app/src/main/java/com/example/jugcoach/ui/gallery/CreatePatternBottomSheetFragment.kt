package com.example.jugcoach.ui.gallery

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AlertDialog
import androidx.core.view.isVisible
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import com.example.jugcoach.R
import com.example.jugcoach.databinding.BottomSheetCreatePatternBinding
import com.example.jugcoach.databinding.DialogPatternSelectionBinding
import com.example.jugcoach.data.entity.Pattern
import com.example.jugcoach.ui.adapters.PatternAdapter
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.chip.Chip
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class CreatePatternBottomSheetFragment : BottomSheetDialogFragment() {
    private var _binding: BottomSheetCreatePatternBinding? = null
    private val binding get() = _binding!!
    private val viewModel: CreatePatternViewModel by viewModels()
    private var dialog: AlertDialog? = null
    private var dialogBinding: DialogPatternSelectionBinding? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = BottomSheetCreatePatternBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupNumBallsChips()
        setupVideoTimeInputs()
        setupFormListeners()
        setupButtons()
        observeState()
    }

    private fun observeState() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.uiState.collect { state ->
                updateUiFromState(state)
            }
        }
    }

    private fun updateUiFromState(state: CreatePatternUiState) {
        // Update error states
        binding.nameLayout.error = state.nameError
        binding.difficultyLayout.error = state.difficultyError
        binding.siteswapLayout.error = state.siteswapError
        binding.videoLayout.error = state.videoUrlError
        binding.gifUrlLayout.error = state.gifUrlError
        binding.tutorialUrlLayout.error = state.tutorialUrlError

        // Update video time error if visible
        if (binding.videoTimeLayout.isVisible) {
            binding.startTimeLayout.error = state.videoTimeError
            binding.endTimeLayout.error = state.videoTimeError
        }

        // Update available tags
        binding.availableTagsGroup.removeAllViews()
        state.availableTags.forEach { tag ->
            val chip = Chip(requireContext()).apply {
                text = tag
                isCheckable = true
                isChecked = tag in state.tags
                setOnCheckedChangeListener { _, isChecked ->
                    if (isChecked) {
                        viewModel.addTag(tag)
                    } else {
                        viewModel.removeTag(tag)
                    }
                }
            }
            binding.availableTagsGroup.addView(chip)
        }

        // Update selected tags
        binding.selectedTagsGroup.removeAllViews()
        state.tags.forEach { tag ->
            val chip = Chip(requireContext()).apply {
                text = tag
                isCloseIconVisible = true
                setOnCloseIconClickListener {
                    viewModel.removeTag(tag)
                }
            }
            binding.selectedTagsGroup.addView(chip)
        }

        // Update prerequisites
        binding.prereqsGroup.removeAllViews()
        state.prerequisites.forEach { patternId ->
            val chip = Chip(requireContext()).apply {
                text = patternId // TODO: Get pattern name from ID
                isCloseIconVisible = true
                setOnCloseIconClickListener {
                    viewModel.removePrerequisite(patternId)
                }
            }
            binding.prereqsGroup.addView(chip)
        }

        // Update related patterns
        binding.relatedGroup.removeAllViews()
        state.relatedPatterns.forEach { patternId ->
            val chip = Chip(requireContext()).apply {
                text = patternId // TODO: Get pattern name from ID
                isCloseIconVisible = true
                setOnCloseIconClickListener {
                    viewModel.removeRelatedPattern(patternId)
                }
            }
            binding.relatedGroup.addView(chip)
        }

        // Update dependent patterns
        binding.dependentsGroup.removeAllViews()
        state.dependentPatterns.forEach { patternId ->
            val chip = Chip(requireContext()).apply {
                text = patternId // TODO: Get pattern name from ID
                isCloseIconVisible = true
                setOnCloseIconClickListener {
                    viewModel.removeDependentPattern(patternId)
                }
            }
            binding.dependentsGroup.addView(chip)
        }

        // Handle save completion
        if (state.isSaved) {
            dismiss()
        }
    }

    private fun setupNumBallsChips() {
        binding.numBallsGroup.removeAllViews()
        (1..11).forEach { num ->
            val chip = Chip(context).apply {
                id = View.generateViewId()
                text = num.toString()
                isCheckable = true
                isCheckedIconVisible = true
                setOnCheckedChangeListener { _, isChecked ->
                    if (isChecked) {
                        viewModel.updateNumBalls(num.toString())
                    }
                }
            }
            binding.numBallsGroup.addView(chip)
        }
    }

    private fun setupVideoTimeInputs() {
        binding.toggleTimeButton.setOnClickListener {
            binding.videoTimeLayout.isVisible = !binding.videoTimeLayout.isVisible
            binding.toggleTimeButton.text = if (binding.videoTimeLayout.isVisible) {
                "Hide Time Range"
            } else {
                "Add Time Range"
            }
        }
    }

    private fun setupFormListeners() {
        // Name input
        binding.nameInput.doAfterTextChanged { text ->
            viewModel.updateName(text?.toString() ?: "")
        }

        // Difficulty input
        binding.difficultyInput.doAfterTextChanged { text ->
            viewModel.updateDifficulty(text?.toString() ?: "")
        }

        // Siteswap input
        binding.siteswapInput.doAfterTextChanged { text ->
            viewModel.updateSiteswap(text?.toString() ?: "")
        }

        // Video URL input
        binding.videoInput.doAfterTextChanged { text ->
            viewModel.updateVideoUrl(text?.toString() ?: "")
        }

        // Video time inputs
        binding.startTimeInput.doAfterTextChanged { text ->
            viewModel.updateVideoStartTime(text?.toString() ?: "")
        }
        binding.endTimeInput.doAfterTextChanged { text ->
            viewModel.updateVideoEndTime(text?.toString() ?: "")
        }

        // GIF URL input
        binding.gifUrlInput.doAfterTextChanged { text ->
            viewModel.updateGifUrl(text?.toString() ?: "")
        }

        // Tutorial URL input
        binding.tutorialUrlInput.doAfterTextChanged { text ->
            viewModel.updateTutorialUrl(text?.toString() ?: "")
        }

        // Explanation input
        binding.explanationInput.doAfterTextChanged { text ->
            viewModel.updateExplanation(text?.toString() ?: "")
        }
    }

    private fun setupButtons() {
        // Pattern relation buttons
        binding.addPrereqsButton.setOnClickListener {
            showPatternSelectionDialog("Select Prerequisites") { pattern ->
                viewModel.addPrerequisite(pattern.id)
            }
        }

        binding.addRelatedButton.setOnClickListener {
            showPatternSelectionDialog("Select Related Pattern") { pattern ->
                viewModel.addRelatedPattern(pattern.id)
            }
        }

        binding.addDependentsButton.setOnClickListener {
            showPatternSelectionDialog("Select Dependent") { pattern ->
                viewModel.addDependentPattern(pattern.id)
            }
        }

        // Action buttons
        binding.saveButton.setOnClickListener {
            viewModel.savePattern()
        }

        binding.cancelButton.setOnClickListener {
            dismiss()
        }
    }

    private fun showPatternSelectionDialog(title: String, onPatternSelected: (Pattern) -> Unit) {
        val dialogBinding = DialogPatternSelectionBinding.inflate(LayoutInflater.from(context))
        this.dialogBinding = dialogBinding
        
        val adapter = PatternAdapter { pattern ->
            onPatternSelected(pattern)
            dialog?.dismiss()
        }
        dialogBinding.patternsList.adapter = adapter
        
        // Set initial list and observe changes
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.availablePatterns.collect { patterns ->
                adapter.submitList(patterns)
            }
        }

        // Setup search functionality
        dialogBinding.searchInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                val searchText = s?.toString()?.lowercase() ?: ""
                viewLifecycleOwner.lifecycleScope.launch {
                    viewModel.availablePatterns.collect { patterns ->
                        val filteredPatterns = patterns.filter { pattern ->
                            pattern.name.lowercase().contains(searchText) ||
                            pattern.tags.any { it.lowercase().contains(searchText) }
                        }
                        adapter.submitList(filteredPatterns)
                    }
                }
            }
        })

        val newDialog = MaterialAlertDialogBuilder(requireContext())
            .setTitle(title)
            .setView(dialogBinding.root)
            .setNegativeButton(R.string.cancel, null)
            .create()
        
        dialog = newDialog
        newDialog.show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        dialog?.dismiss()
        dialog = null
        dialogBinding = null
        _binding = null
    }

    companion object {
        const val TAG = "CreatePatternBottomSheetFragment"
        private const val ARG_SOURCE_PATTERN_ID = "sourcePatternId"
        private const val ARG_RELATIONSHIP_TYPE = "relationshipType"

        fun newInstance(sourcePatternId: String, relationshipType: String): CreatePatternBottomSheetFragment {
            return CreatePatternBottomSheetFragment().apply {
                arguments = Bundle().apply {
                    putString(ARG_SOURCE_PATTERN_ID, sourcePatternId)
                    putString(ARG_RELATIONSHIP_TYPE, relationshipType)
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Get source pattern ID and relationship type from arguments
        arguments?.let { args ->
            val sourcePatternId = args.getString(ARG_SOURCE_PATTERN_ID)
            val relationshipType = args.getString(ARG_RELATIONSHIP_TYPE)
            
            if (sourcePatternId != null && relationshipType != null) {
                viewModel.initializeFromSourcePattern(sourcePatternId, relationshipType)
            }
        }
    }
}