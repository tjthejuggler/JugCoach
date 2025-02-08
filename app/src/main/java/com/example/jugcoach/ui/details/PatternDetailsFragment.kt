package com.example.jugcoach.ui.details

import android.content.Intent
import com.example.jugcoach.glide.GlideApp
import android.net.Uri
import android.text.format.DateFormat
import com.bumptech.glide.load.DecodeFormat
import com.bumptech.glide.request.RequestOptions
import com.bumptech.glide.load.engine.DiskCacheStrategy
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.RotateAnimation
import android.view.animation.Animation
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import com.example.jugcoach.R
import com.example.jugcoach.data.entity.Pattern
import com.example.jugcoach.data.entity.Run
import com.example.jugcoach.databinding.FragmentPatternDetailsBinding
import com.example.jugcoach.ui.adapters.PatternAdapter
import com.google.android.material.chip.Chip
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class PatternDetailsFragment : Fragment() {

    private var _binding: FragmentPatternDetailsBinding? = null
    private val binding get() = _binding!!

    private val viewModel: PatternDetailsViewModel by viewModels()
    
    private val prerequisitesAdapter = PatternAdapter { pattern ->
        navigateToPattern(pattern.id)
    }
    
    private val dependentsAdapter = PatternAdapter { pattern ->
        navigateToPattern(pattern.id)
    }
    
    private val relatedAdapter = PatternAdapter { pattern ->
        navigateToPattern(pattern.id)
    }

    private val runHistoryAdapter = RunHistoryAdapter()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentPatternDetailsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        setupToolbar()
        setupRecyclerViews()
        setupCollapsibleSections()
        setupRunInput()
        observeUiState()
        observeRelatedPatterns()
    }

    private fun setupToolbar() {
        binding.toolbar.setNavigationOnClickListener {
            findNavController().navigateUp()
        }
        
        binding.toolbar.setOnMenuItemClickListener { menuItem ->
            when (menuItem.itemId) {
                R.id.action_edit -> {
                    // TODO: Implement edit dialog
                    true
                }
                R.id.action_delete -> {
                    showDeleteConfirmation()
                    true
                }
                else -> false
            }
        }
    }

    private fun setupRecyclerViews() {
        binding.prerequisitesList.adapter = prerequisitesAdapter
        binding.dependentsList.adapter = dependentsAdapter
        binding.relatedList.adapter = relatedAdapter
        binding.runHistoryList.adapter = runHistoryAdapter
    }

    private fun setupCollapsibleSections() {
        binding.apply {
            prerequisitesHeader.setOnClickListener {
                toggleSection(prerequisitesList, prerequisitesExpandIcon)
            }
            dependentsHeader.setOnClickListener {
                toggleSection(dependentsList, dependentsExpandIcon)
            }
            relatedHeader.setOnClickListener {
                toggleSection(relatedList, relatedExpandIcon)
            }
            historyHeader.setOnClickListener {
                toggleSection(runHistoryList, historyExpandIcon)
            }
        }
    }

    private fun setupRunInput() {
        binding.apply {
            // Only allow one input type at a time
            catchesInput.setOnFocusChangeListener { _, hasFocus ->
                if (hasFocus) timeInput.text?.clear()
            }
            timeInput.setOnFocusChangeListener { _, hasFocus ->
                if (hasFocus) catchesInput.text?.clear()
            }

            submitRunButton.setOnClickListener {
                val catches = catchesInput.text?.toString()?.toIntOrNull()
                val time = timeInput.text?.toString()?.toLongOrNull()
                val isCleanEnd = cleanEndCheckbox.isChecked

                if (catches == null && time == null) {
                    Snackbar.make(root, "Please enter either catches or time", Snackbar.LENGTH_SHORT).show()
                    return@setOnClickListener
                }

                viewModel.addRun(catches, time, isCleanEnd)
                
                // Clear inputs after submission
                catchesInput.text?.clear()
                timeInput.text?.clear()
                cleanEndCheckbox.isChecked = false
            }
        }
    }

    private fun toggleSection(recyclerView: View, icon: View) {
        val wasExpanded = recyclerView.isVisible
        recyclerView.isVisible = !wasExpanded

        val fromDegrees = if (wasExpanded) 180f else 0f
        val toDegrees = if (wasExpanded) 0f else 180f

        val rotate = RotateAnimation(
            fromDegrees, toDegrees,
            Animation.RELATIVE_TO_SELF, 0.5f,
            Animation.RELATIVE_TO_SELF, 0.5f
        ).apply {
            duration = 300
            fillAfter = true
        }
        icon.startAnimation(rotate)
    }

    private fun observeUiState() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    when (state) {
                        is PatternDetailsUiState.Loading -> showLoading()
                        is PatternDetailsUiState.Success -> showPattern(state.pattern, state.runHistory)
                        is PatternDetailsUiState.Error -> showError(state.message)
                        is PatternDetailsUiState.Deleted -> handlePatternDeleted()
                    }
                }
            }
        }
    }

    private fun observeRelatedPatterns() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.prerequisites.collect { patterns ->
                        android.util.Log.d("PatternDetails", "Collecting prerequisites: ${patterns.size} items")
                        prerequisitesAdapter.submitList(patterns)
                        binding.apply {
                            prerequisitesCard.isVisible = patterns.isNotEmpty()
                            android.util.Log.d("PatternDetails", "Prerequisites visibility: ${prerequisitesCard.isVisible}")
                        }
                    }
                }
                launch {
                    viewModel.dependents.collect { patterns ->
                        android.util.Log.d("PatternDetails", "Collecting dependents: ${patterns.size} items")
                        dependentsAdapter.submitList(patterns)
                        binding.apply {
                            dependentsCard.isVisible = patterns.isNotEmpty()
                            android.util.Log.d("PatternDetails", "Dependents visibility: ${dependentsCard.isVisible}")
                        }
                    }
                }
                launch {
                    viewModel.related.collect { patterns ->
                        android.util.Log.d("PatternDetails", "Collecting related: ${patterns.size} items")
                        relatedAdapter.submitList(patterns)
                        binding.apply {
                            relatedCard.isVisible = patterns.isNotEmpty()
                            android.util.Log.d("PatternDetails", "Related visibility: ${relatedCard.isVisible}")
                        }
                    }
                }
            }
        }
    }

    private fun showLoading() {
        // TODO: Implement loading state
    }

    private fun showPattern(pattern: Pattern, runHistory: List<Run>) {
        binding.apply {
            patternName.text = pattern.name
            patternDescription.text = pattern.explanation

            // Load animation GIF
            pattern.gifUrl?.let { url ->
                patternAnimation.isVisible = true
                val options = RequestOptions()
                    .format(DecodeFormat.PREFER_RGB_565)
                    .centerCrop()
                    .diskCacheStrategy(DiskCacheStrategy.ALL)

                GlideApp.with(this@PatternDetailsFragment)
                    .asGif()
                    .load(url)
                    .apply(options)
                    .into(patternAnimation)
            } ?: run {
                patternAnimation.isVisible = false
            }
            
            // Set difficulty chip
            pattern.difficulty?.let { difficulty ->
                difficultyChip.text = getString(R.string.difficulty_format, difficulty)
                difficultyChip.isVisible = true
            } ?: run {
                difficultyChip.isVisible = false
            }

            // Set number of balls chip
            pattern.num?.let { num ->
                numChip.text = getString(R.string.balls_format, num)
                numChip.isVisible = true
            } ?: run {
                numChip.isVisible = false
            }

            // Set siteswap chip
            pattern.siteswap?.let { siteswap ->
                siteswapChip.text = siteswap
                siteswapChip.isVisible = true
            } ?: run {
                siteswapChip.isVisible = false
            }

            // Set video button
            pattern.video?.let { videoUrl ->
                videoButton.isVisible = true
                videoButton.setOnClickListener {
                    // Open video URL in browser
                    startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(videoUrl)))
                }
            } ?: run {
                videoButton.isVisible = false
            }

            // Set URL button
            pattern.url?.let { url ->
                urlButton.isVisible = true
                urlButton.setOnClickListener {
                    // Open URL in browser
                    startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                }
            } ?: run {
                urlButton.isVisible = false
            }

            // Show personal record
            pattern.record?.let { record ->
                recordCard.isVisible = true
                recordCatches.text = getString(R.string.catches_format, record.catches)
                recordDate.text = DateFormat.getDateFormat(requireContext())
                    .format(record.date)
            } ?: run {
                recordCard.isVisible = false
            }

            // Show buttons container only if video or url exists
            buttonsContainer.isVisible = pattern.video != null || pattern.url != null

            // Update run history
            runHistoryAdapter.submitList(runHistory)

            // Create tag chips
            tagsGroup.removeAllViews()
            pattern.tags.forEach { tag ->
                val chip = Chip(requireContext()).apply {
                    text = tag
                    isClickable = false
                }
                tagsGroup.addView(chip)
            }
        }
    }

    private fun showError(message: String) {
        Snackbar.make(binding.root, message, Snackbar.LENGTH_LONG).show()
    }

    private fun handlePatternDeleted() {
        findNavController().navigateUp()
    }

    private fun showDeleteConfirmation() {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.delete_pattern_title)
            .setMessage(R.string.delete_pattern_message)
            .setPositiveButton(R.string.delete) { _, _ ->
                viewModel.deletePattern()
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun navigateToPattern(patternId: String) {
        val action = PatternDetailsFragmentDirections
            .actionPatternDetailsFragmentSelf(patternId)
        findNavController().navigate(action)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
