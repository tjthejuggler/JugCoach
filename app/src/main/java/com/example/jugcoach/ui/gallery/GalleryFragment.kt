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
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import com.example.jugcoach.ui.gallery.GalleryViewModel
import com.example.jugcoach.ui.gallery.GalleryUiState
import com.example.jugcoach.ui.gallery.SortOrder

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

    private lateinit var filterBottomSheet: FilterBottomSheetFragment

    private fun setupSearch() {
        binding.searchInput.doAfterTextChanged { text ->
            viewModel.updateSearchQuery(text?.toString() ?: "")
        }

        binding.filterButton.setOnClickListener {
            showFilterBottomSheet()
        }

        binding.sortButton.setOnClickListener {
            showSortDialog()
        }
    }

    private fun showFilterBottomSheet() {
        filterBottomSheet = FilterBottomSheetFragment().apply {
            setFilterListener(object : FilterBottomSheetFragment.FilterListener {
                override fun onFiltersApplied(filters: FilterOptions) {
                    viewModel.updateFilters(filters)
                }
            })
        }
        filterBottomSheet.show(childFragmentManager, FilterBottomSheetFragment.TAG)
        // Set available tags after showing the fragment to ensure view is created
        filterBottomSheet.setAvailableTags(viewModel.uiState.value.availableTags)
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

            // Update filter bottom sheet if it's showing
            if (::filterBottomSheet.isInitialized && filterBottomSheet.isVisible) {
                filterBottomSheet.setAvailableTags(state.availableTags)
            }
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
