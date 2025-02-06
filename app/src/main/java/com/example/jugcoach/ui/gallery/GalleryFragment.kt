package com.example.jugcoach.ui.gallery

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.example.jugcoach.R
import com.example.jugcoach.data.entity.Pattern
import com.example.jugcoach.databinding.FragmentGalleryBinding
import com.example.jugcoach.ui.adapters.PatternAdapter
import com.google.android.material.chip.Chip
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class GalleryFragment : Fragment() {
    companion object {
        private const val TAG = "GalleryFragment"
    }
    private var _binding: FragmentGalleryBinding? = null
    private val binding get() = _binding!!
    private lateinit var viewModel: GalleryViewModel
    private lateinit var patternAdapter: PatternAdapter

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        viewModel = ViewModelProvider(this).get(GalleryViewModel::class.java)
        _binding = FragmentGalleryBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupRecyclerView()
        setupSearch()
        observeUiState()
    }

    private fun setupRecyclerView() {
        patternAdapter = PatternAdapter { pattern ->
            onPatternSelected(pattern)
        }
        binding.patternsList.adapter = patternAdapter
    }

    private fun setupSearch() {
        binding.searchInput.doAfterTextChanged { text ->
            viewModel.updateSearchQuery(text?.toString() ?: "")
        }

        binding.difficultyToggle.apply {
            check(R.id.difficulty_all)
            addOnButtonCheckedListener { _, checkedId, isChecked ->
                if (isChecked) {
                    val filter = when (checkedId) {
                        R.id.difficulty_all -> DifficultyFilter.ALL
                        R.id.difficulty_beginner -> DifficultyFilter.BEGINNER
                        R.id.difficulty_advanced -> DifficultyFilter.ADVANCED
                        else -> DifficultyFilter.ALL
                    }
                    viewModel.updateDifficultyFilter(filter)
                }
            }
        }

        binding.sortButton.setOnClickListener {
            showSortDialog()
        }
    }

    private fun showSortDialog() {
        val items = arrayOf(
            "Name (A-Z)" to SortOrder.NAME_ASC,
            "Name (Z-A)" to SortOrder.NAME_DESC,
            "Difficulty (Easy to Hard)" to SortOrder.DIFFICULTY_ASC,
            "Difficulty (Hard to Easy)" to SortOrder.DIFFICULTY_DESC
        )

        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Sort By")
            .setItems(items.map { it.first }.toTypedArray()) { _, which ->
                viewModel.updateSortOrder(items[which].second)
            }
            .show()
    }

    private fun updateTagChips(tags: Set<String>, selectedTags: Set<String>) {
        binding.tagGroup.removeAllViews()
        tags.forEach { tag ->
            val chip = Chip(context).apply {
                text = tag
                isCheckable = true
                isChecked = tag in selectedTags
                setOnCheckedChangeListener { _, _ ->
                    viewModel.toggleTag(tag)
                }
            }
            binding.tagGroup.addView(chip)
        }
    }

    private fun observeUiState() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.uiState.collectLatest { state ->
                updateUi(state)
            }
        }
    }

    private fun updateUi(state: GalleryUiState) {
        binding.apply {
            progressIndicator.isVisible = state.isLoading
            emptyView.isVisible = !state.isLoading && state.patterns.isEmpty()
            patternsList.isVisible = !state.isLoading && state.patterns.isNotEmpty()
            
            if (!state.isLoading && state.patterns.isNotEmpty()) {
                patternAdapter.submitList(state.patterns)
            }

            // Update search if needed
            if (searchInput.text?.toString() != state.searchQuery) {
                searchInput.setText(state.searchQuery)
            }

            // Update difficulty toggle
            when (state.selectedDifficulty) {
                DifficultyFilter.ALL -> difficultyToggle.check(R.id.difficulty_all)
                DifficultyFilter.BEGINNER -> difficultyToggle.check(R.id.difficulty_beginner)
                DifficultyFilter.ADVANCED -> difficultyToggle.check(R.id.difficulty_advanced)
            }

            // Update tag chips
            updateTagChips(state.availableTags, state.selectedTags)
        }
    }

    private fun onPatternSelected(pattern: Pattern) {
        val action = GalleryFragmentDirections
            .actionNavGalleryToPatternDetailsFragment(pattern.id)
        findNavController().navigate(action)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
