package com.example.jugcoach.ui.gallery

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import com.example.jugcoach.R
import com.example.jugcoach.data.entity.Pattern
import com.example.jugcoach.databinding.DialogPatternSelectionBinding
import com.example.jugcoach.ui.adapters.PatternAdapter
import com.google.android.material.chip.Chip
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

@AndroidEntryPoint
class PatternSelectionDialog : DialogFragment() {
    private var _binding: DialogPatternSelectionBinding? = null
    private val binding get() = _binding!!
    private val viewModel: PatternSelectionViewModel by viewModels()
    private lateinit var patternAdapter: PatternAdapter
    private var selectionListener: PatternSelectionListener? = null

    enum class SelectionType {
        PREREQUISITES,
        RELATED,
        DEPENDENTS
    }

    interface PatternSelectionListener {
        fun onPatternsSelected(patterns: List<Pattern>)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setStyle(STYLE_NORMAL, com.google.android.material.R.style.ThemeOverlay_MaterialComponents_Dialog_Alert)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = DialogPatternSelectionBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        // Get selection type from arguments
        arguments?.getString(ARG_SELECTION_TYPE)?.let { typeStr ->
            val type = SelectionType.valueOf(typeStr)
            viewModel.setSelectionType(type)
            
            // Update title based on type
            binding.title.text = when (type) {
                SelectionType.PREREQUISITES -> "Select Prerequisites"
                SelectionType.RELATED -> "Select Related Patterns"
                SelectionType.DEPENDENTS -> "Select Dependent Patterns"
            }
        }

        setupRecyclerView()
        setupSearch()
        setupButtons()
        observeState()
    }

    private fun setupRecyclerView() {
        patternAdapter = PatternAdapter { pattern ->
            viewModel.togglePatternSelection(pattern)
        }
        binding.patternsList.adapter = patternAdapter
    }

    private fun setupSearch() {
        binding.searchInput.doAfterTextChanged { text ->
            viewModel.updateSearchQuery(text?.toString() ?: "")
        }
    }

    private fun setupButtons() {
        binding.cancelButton.setOnClickListener {
            dismiss()
        }

        binding.confirmButton.setOnClickListener {
            viewModel.getSelectedPatterns().let { patterns ->
                selectionListener?.onPatternsSelected(patterns)
                dismiss()
            }
        }
    }

    private fun observeState() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.uiState.collectLatest { state ->
                updateUi(state)
            }
        }
    }

    private fun updateUi(state: PatternSelectionUiState) {
        // Update tag filters
        binding.tagFilterGroup.removeAllViews()
        state.availableTags.forEach { tag ->
            val chip = Chip(requireContext()).apply {
                text = tag
                isCheckable = true
                isChecked = tag in state.selectedTags
                setOnCheckedChangeListener { _, isChecked ->
                    if (isChecked) {
                        viewModel.addTagFilter(tag)
                    } else {
                        viewModel.removeTagFilter(tag)
                    }
                }
            }
            binding.tagFilterGroup.addView(chip)
        }

        // Update pattern list
        patternAdapter.submitList(state.patterns)
    }

    fun setSelectionListener(listener: PatternSelectionListener) {
        selectionListener = listener
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        const val TAG = "PatternSelectionDialog"
        private const val ARG_SELECTION_TYPE = "selection_type"

        fun newInstance(type: SelectionType): PatternSelectionDialog {
            return PatternSelectionDialog().apply {
                arguments = Bundle().apply {
                    putString(ARG_SELECTION_TYPE, type.name)
                }
            }
        }
    }
}