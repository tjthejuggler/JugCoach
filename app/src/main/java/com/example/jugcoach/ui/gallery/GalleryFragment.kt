package com.example.jugcoach.ui.gallery

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.example.jugcoach.data.entity.Pattern
import com.example.jugcoach.databinding.FragmentGalleryBinding
import com.example.jugcoach.ui.adapters.PatternAdapter
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

@AndroidEntryPoint
class GalleryFragment : Fragment() {
    companion object {
        private const val TAG = "GalleryFragment"
    }
    private var _binding: FragmentGalleryBinding? = null
    private val binding get() = _binding!!
    private val viewModel: GalleryViewModel by viewModels()
    private lateinit var patternAdapter: PatternAdapter

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
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
        binding.patternsList.apply {
            adapter = patternAdapter
            setItemAnimator(null) // Disable animations to prevent position tracking
        }
    }

    private lateinit var filterBottomSheet: FilterBottomSheetFragment

    private fun setupSearch() {
        binding.searchInput.doAfterTextChanged { text ->
            viewModel.updateSearchQuery(text?.toString() ?: "")
        }

        binding.filterButton.setOnClickListener {
            showFilterBottomSheet(false)
        }

        binding.sortButton.setOnClickListener {
            showFilterBottomSheet(true)
        }
    }

    private fun showFilterBottomSheet(showSortTab: Boolean) {
        filterBottomSheet = FilterBottomSheetFragment().apply {
            setFilterListener(object : FilterBottomSheetFragment.FilterListener {
                override fun onFiltersApplied(filters: FilterOptions, sortOrder: SortOrder) {
                    viewModel.updateFilters(filters)
                    viewModel.updateSortOrder(sortOrder)
                }
            })
            arguments = Bundle().apply {
                putBoolean("show_sort_tab", showSortTab)
                putString("current_sort_order", viewModel.uiState.value.sortOrder.name)
            }
        }
        filterBottomSheet.show(childFragmentManager, FilterBottomSheetFragment.TAG)
        // Set available tags after showing the fragment to ensure view is created
        filterBottomSheet.setAvailableTags(viewModel.uiState.value.availableTags)
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
            
            // Update list and scroll to bottom after update
            if (!state.isLoading && state.patterns.isNotEmpty()) {
                patternAdapter.submitList(state.patterns) {
                    // Always scroll to top after list updates to show the most relevant item
                    patternsList.scrollToPosition(0)
                    viewModel.scrollHandled()
                }
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
