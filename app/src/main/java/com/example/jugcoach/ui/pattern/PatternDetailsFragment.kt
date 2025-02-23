package com.example.jugcoach.ui.pattern

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
import android.widget.TextView
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import com.example.jugcoach.R
import com.example.jugcoach.data.entity.Pattern
import com.example.jugcoach.data.entity.Run
import com.example.jugcoach.data.entity.CoachProposal
import com.example.jugcoach.databinding.FragmentPatternDetailsBinding
import com.example.jugcoach.databinding.DialogAddRunBinding
import com.example.jugcoach.ui.adapters.PatternAdapter
import com.google.android.material.chip.Chip
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.example.jugcoach.ui.video.VideoPlayerView
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class PatternDetailsFragment : Fragment() {

    private var _binding: FragmentPatternDetailsBinding? = null
    private val binding get() = _binding!!
    private lateinit var videoPlayer: VideoPlayerView

    private val args: PatternDetailsFragmentArgs by navArgs()
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
    
    private val proposalAdapter = ProposalAdapter(
        onApprove = { proposal ->
            viewModel.approveProposal(proposal)
        },
        onReject = { proposal ->
            viewModel.rejectProposal(proposal)
        }
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        android.util.Log.d("PatternDetails", "Fragment created with pattern ID: ${args.patternId}")
        viewModel.setPatternId(args.patternId)
    }

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
        
        // Initialize video player immediately after binding
        videoPlayer = binding.videoPlayer.apply {
            isVisible = false // Hide initially
            preparePlayer() // Initialize player
        }
        
        setupToolbar()
        setupRecyclerViews()
        setupCollapsibleSections()
        observeUiState()
        observeRelatedPatterns()
        observePendingProposals()

        // Reload data when fragment becomes visible
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.RESUMED) {
                viewModel.loadPattern()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // Prepare player when fragment becomes visible
        if (videoPlayer.isVisible) {
            videoPlayer.preparePlayer()
        }
    }

    override fun onPause() {
        super.onPause()
        // Release player when fragment is not visible
        videoPlayer.releasePlayer()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        videoPlayer.releasePlayer()
        _binding = null
    }

    private fun setupToolbar() {
        binding.toolbar.setNavigationOnClickListener {
            findNavController().navigateUp()
        }
        
        binding.toolbar.setOnMenuItemClickListener { menuItem ->
            when (menuItem.itemId) {
                R.id.action_add_run -> {
                    showAddRunDialog()
                    true
                }
                R.id.action_edit -> {
                    val action = PatternDetailsFragmentDirections
                        .actionPatternDetailsFragmentToEditPatternFragment(viewModel.getCurrentPatternId())
                    findNavController().navigate(action)
                    true
                }
                else -> false
            }
        }
    }

    private fun showAddRunDialog() {
        val dialogBinding = DialogAddRunBinding.inflate(LayoutInflater.from(context))
        var dialog: androidx.appcompat.app.AlertDialog? = null

        // Set up chat timer button click
        dialogBinding.chatTimerButton.setOnClickListener {
            dialog?.dismiss() // Dismiss dialog first
            // Then navigate to chat
            val action = PatternDetailsFragmentDirections
                .actionPatternDetailsFragmentToChatFragment(
                    startTimer = true,
                    patternId = viewModel.getCurrentPatternId()
                )
            findNavController().navigate(action)
        }

        dialog = MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.add_new_run)
            .setView(dialogBinding.root)
            .setPositiveButton(R.string.add_run) { _, _ ->
                // Manual input mode
                val catches = dialogBinding.catchesInput.text?.toString()?.toIntOrNull()
                val duration = dialogBinding.timeInput.text?.toString()?.toLongOrNull()
                val isCleanEnd = dialogBinding.cleanEndCheckbox.isChecked

                when {
                    catches == null && duration == null -> {
                        Snackbar.make(binding.root, getString(R.string.enter_catches_or_time), Snackbar.LENGTH_SHORT).show()
                        return@setPositiveButton
                    }
                    catches != null && duration != null -> {
                        // Both provided - calculate catches per minute
                        viewModel.addRun(catches, duration, isCleanEnd)
                    }
                    else -> {
                        // Only one provided - that's okay too
                        viewModel.addRun(catches, duration, isCleanEnd)
                    }
                }
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun setupRecyclerViews() {
        binding.prerequisitesList.adapter = prerequisitesAdapter
        binding.dependentsList.adapter = dependentsAdapter
        binding.relatedList.adapter = relatedAdapter
        binding.runHistoryList.adapter = runHistoryAdapter
        binding.proposalsList.adapter = proposalAdapter
    }

    private fun setupCollapsibleSections() {
        binding.apply {
            // Initialize all sections as collapsed
            prerequisitesList.isVisible = false
            prerequisitesExpandIcon.rotation = 0f
            
            dependentsList.isVisible = false
            dependentsExpandIcon.rotation = 0f
            
            relatedList.isVisible = false
            relatedExpandIcon.rotation = 0f
            
            runHistoryList.isVisible = false
            historyExpandIcon.rotation = 0f
            
            proposalsList.isVisible = false
            proposalsExpandIcon.rotation = 0f

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
            proposalsHeader.setOnClickListener {
                toggleSection(proposalsList, proposalsExpandIcon)
            }
        }
    }

    private fun toggleSection(recyclerView: View, icon: View) {
        val wasExpanded = recyclerView.isVisible
        recyclerView.isVisible = !wasExpanded

        // Update icon rotation
        icon.animate()
            .rotation(if (wasExpanded) 0f else 180f)
            .setDuration(300)
            .start()
    }

    private fun observeUiState() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                android.util.Log.d("PatternDetails", "Starting to observe UI state")
                viewModel.uiState.collect { state ->
                    android.util.Log.d("PatternDetails", "Received UI state update: $state")
                    when (state) {
                        is PatternDetailsUiState.Loading -> {
                            android.util.Log.d("PatternDetails", "Showing loading state")
                            showLoading()
                        }
                        is PatternDetailsUiState.Success -> {
                            android.util.Log.d("PatternDetails", "Showing pattern: ${state.pattern.name}")
                            android.util.Log.d("PatternDetails", "Pattern details: id=${state.pattern.id}, " +
                                "difficulty=${state.pattern.difficulty}, " +
                                "explanation=${state.pattern.explanation?.take(50)}...")
                            showPattern(state.pattern, state.runHistory)
                        }
                        is PatternDetailsUiState.Error -> {
                            android.util.Log.e("PatternDetails", "Error showing pattern: ${state.message}")
                            showError(state.message)
                        }
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
                        binding.apply {
                            prerequisitesAdapter.submitList(patterns)
                            prerequisitesCard.isVisible = patterns.isNotEmpty()
                            android.util.Log.d("PatternDetails", "Prerequisites visibility: ${prerequisitesCard.isVisible}")
                        }
                    }
                }
                launch {
                    viewModel.dependents.collect { patterns ->
                        android.util.Log.d("PatternDetails", "Collecting dependents: ${patterns.size} items")
                        binding.apply {
                            dependentsAdapter.submitList(patterns)
                            dependentsCard.isVisible = patterns.isNotEmpty()
                            android.util.Log.d("PatternDetails", "Dependents visibility: ${dependentsCard.isVisible}")
                        }
                    }
                }
                launch {
                    viewModel.related.collect { patterns ->
                        android.util.Log.d("PatternDetails", "Collecting related: ${patterns.size} items")
                        binding.apply {
                            relatedAdapter.submitList(patterns)
                            relatedCard.isVisible = patterns.isNotEmpty()
                            android.util.Log.d("PatternDetails", "Related visibility: ${relatedCard.isVisible}")
                        }
                    }
                }
            }
        }
    }

    private fun observePendingProposals() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.pendingProposals.collect { proposals ->
                    binding.apply {
                        proposalAdapter.submitList(proposals)
                        proposalsCard.isVisible = proposals.isNotEmpty()
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
            siteswapChip.apply {
                text = pattern.siteswap?.takeIf { it.isNotEmpty() } ?: getString(R.string.no_siteswap)
                isVisible = true
            }

            // Set up video player
            if (pattern.video != null) {
                binding.videoContainer.isVisible = true
                binding.patternAnimation.isVisible = false
                videoPlayer.isVisible = true
                videoPlayer.setPattern(pattern)
            } else {
                binding.videoContainer.isVisible = false
                videoPlayer.isVisible = false
                binding.patternAnimation.isVisible = pattern.gifUrl != null
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

            // Set external video button
            pattern.video?.let { video ->
                externalVideoButton.isVisible = true
                externalVideoButton.setOnClickListener {
                    // Open video URL in browser
                    startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(video)))
                }
            } ?: run {
                externalVideoButton.isVisible = false
            }

            // Show buttons container if either URL or video exists
            buttonsContainer.isVisible = pattern.url != null || pattern.video != null

            // Show personal record
            pattern.record?.let { record ->
                recordCard.isVisible = true
                recordCatches.text = getString(R.string.catches_format, record.catches)
                recordDate.text = DateFormat.getDateFormat(requireContext())
                    .format(record.date)
            } ?: run {
                recordCard.isVisible = false
            }

            // Show buttons container only if url exists
            buttonsContainer.isVisible = pattern.url != null

            // Calculate and show overall catches per minute
            val runsWithCatchesAndTime = runHistory.filter { it.catches != null && it.duration != null }
            if (runsWithCatchesAndTime.isNotEmpty()) {
                val totalCatches = runsWithCatchesAndTime.sumOf { it.catches!! }
                val totalSeconds = runsWithCatchesAndTime.sumOf { it.duration!! }
                val overallCpm = (totalCatches.toDouble() / totalSeconds.toDouble()) * 60
                binding.patternCatchesPerMinute.apply {
                    text = getString(R.string.pattern_catches_per_minute, overallCpm)
                    isVisible = true
                }
            } else {
                binding.patternCatchesPerMinute.isVisible = false
            }

            // Update run history
            runHistoryAdapter.submitList(runHistory)
            runHistoryCard.isVisible = runHistory.isNotEmpty()

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

    private fun navigateToPattern(patternId: String) {
        val action = PatternDetailsFragmentDirections
            .actionPatternDetailsFragmentSelf(patternId)
        findNavController().navigate(action)
    }


}