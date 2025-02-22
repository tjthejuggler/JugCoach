package com.example.jugcoach.ui.pattern

import android.app.DatePickerDialog
import android.view.ViewGroup.LayoutParams
import androidx.core.view.children
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.navArgs
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import androidx.appcompat.app.AlertDialog
import kotlinx.coroutines.launch
import com.google.android.material.chip.Chip
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.example.jugcoach.R
import com.example.jugcoach.ui.adapters.PatternAdapter
import com.example.jugcoach.databinding.DialogPatternSelectionBinding
import com.example.jugcoach.databinding.FragmentEditPatternBinding
import com.example.jugcoach.data.entity.Pattern
import com.example.jugcoach.data.entity.Record
import com.example.jugcoach.data.entity.Run
import com.example.jugcoach.data.entity.RunHistory
import dagger.hilt.android.AndroidEntryPoint
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

@AndroidEntryPoint
class EditPatternFragment : Fragment() {
    private val args: EditPatternFragmentArgs by navArgs()
    private var _binding: FragmentEditPatternBinding? = null
    private val binding get() = _binding!!
    private val viewModel: EditPatternViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        viewModel.setPatternId(args.patternId)
    }
    private lateinit var runAdapter: EditRunAdapter
    private val dateFormat = SimpleDateFormat("MMM d, yyyy", Locale.getDefault())
    private val calendar = Calendar.getInstance()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentEditPatternBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupToolbar()
        setupRunAdapter()
        setupDatePickers()
        setupPatternSelectionButtons()
        observePattern()
        observeCoachStatus()
    }

    private fun observeCoachStatus() {
        viewModel.isHeadCoach.observe(viewLifecycleOwner) { isHeadCoach ->
            // Update save button text
            binding.toolbar.menu.findItem(R.id.action_save)?.setTitle(
                if (isHeadCoach) R.string.save else R.string.submit_for_approval
            )
            
            // Show/hide coach notice
            binding.coachNotice.visibility = if (isHeadCoach) View.GONE else View.VISIBLE
        }
    }

    private fun setupToolbar() {
        binding.toolbar.setNavigationOnClickListener {
            findNavController().navigateUp()
        }
        binding.toolbar.setOnMenuItemClickListener { menuItem ->
            when (menuItem.itemId) {
                R.id.action_save -> {
                    savePattern()
                    true
                }
                else -> false
            }
        }
    }

    private fun setupRunAdapter() {
        runAdapter = EditRunAdapter(
            onDateClick = { position, run ->
                showDatePicker(position, run.date) { newDate ->
                    runAdapter.updateRunDate(position, newDate)
                }
            },
            onDelete = { position ->
                runAdapter.removeRun(position)
            }
        )
        binding.runHistoryList.apply {
            layoutManager = LinearLayoutManager(context)
            adapter = runAdapter
        }
    }

    private fun setupDatePickers() {
        binding.recordDateEdit.setOnClickListener {
            val currentDate = binding.recordDateEdit.text.toString()
            val initialDate = if (currentDate.isNotEmpty()) {
                try {
                    dateFormat.parse(currentDate)?.time ?: System.currentTimeMillis()
                } catch (e: Exception) {
                    System.currentTimeMillis()
                }
            } else {
                System.currentTimeMillis()
            }
            showDatePicker(initialDate = initialDate) { date ->
                binding.recordDateEdit.setText(dateFormat.format(Date(date)))
            }
        }
    }

    private fun setupPatternSelectionButtons() {
        binding.addPrerequisiteButton.setOnClickListener {
            showPatternSelectionDialog("Select Prerequisite") { pattern ->
                viewModel.addPrerequisite(pattern.id)
            }
        }
        binding.addDependentButton.setOnClickListener {
            showPatternSelectionDialog("Select Dependent") { pattern ->
                viewModel.addDependent(pattern.id)
            }
        }
        binding.addRelatedButton.setOnClickListener {
            showPatternSelectionDialog("Select Related Pattern") { pattern ->
                viewModel.addRelated(pattern.id)
            }
        }
    }

    private fun observePattern() {
        viewModel.pattern.observe(viewLifecycleOwner) { pattern ->
            pattern?.let { updateUI(it) }
        }

        // Observe available patterns for dialog
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.availablePatterns.collect { patterns ->
                    // Update the dialog adapter if it's showing
                    val currentDialog = dialog
                    val currentBinding = dialogBinding
                    if (currentDialog?.isShowing == true && currentBinding != null) {
                        val searchText = currentBinding.searchInput.text?.toString()?.lowercase() ?: ""
                        val filteredPatterns = patterns.filter { pattern ->
                            pattern.name.lowercase().contains(searchText) ||
                            pattern.tags.any { it.lowercase().contains(searchText) }
                        }
                        (currentBinding.patternsList.adapter as? PatternAdapter)?.submitList(filteredPatterns)
                    }
                }
            }
        }

        // Observe available tags
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.availableTags.collect { tags ->
                    // Update available tags in UI
                    binding.availableTagsGroup.removeAllViews()
                    tags.forEach { tag ->
                        Chip(requireContext()).apply {
                            text = tag
                            isCheckable = true
                            isChecked = tag in (viewModel.pattern.value?.tags ?: emptyList())
                            setOnCheckedChangeListener { _, isChecked ->
                                if (isChecked) {
                                    viewModel.addTag(tag)
                                } else {
                                    viewModel.removeTag(tag)
                                }
                            }
                            binding.availableTagsGroup.addView(this)
                        }
                    }
                }
            }
        }
    }

    private var dialog: AlertDialog? = null
    private var dialogBinding: DialogPatternSelectionBinding? = null

    private fun updateUI(pattern: Pattern) {
        binding.apply {
            nameEdit.setText(pattern.name)
            difficultyEdit.setText(pattern.difficulty)
            numBallsEdit.setText(pattern.num)
            siteswapEdit.setText(pattern.siteswap)
            explanationEdit.setText(pattern.explanation)
            gifUrlEdit.setText(pattern.gifUrl)
            videoUrlEdit.setText(pattern.video)
            videoStartTimeEdit.setText(pattern.videoStartTime?.toString() ?: "")
            videoEndTimeEdit.setText(pattern.videoEndTime?.toString() ?: "")
            externalUrlEdit.setText(pattern.url)

            // Update available tags
            availableTagsGroup.removeAllViews()
            viewModel.availablePatterns.value.flatMap { it.tags }.toSet().forEach { tag ->
                Chip(requireContext()).apply {
                    text = tag
                    isCheckable = true
                    isChecked = tag in pattern.tags
                    setOnCheckedChangeListener { _, isChecked ->
                        if (isChecked) {
                            addTag(tag)
                        } else {
                            removeTag(tag)
                        }
                    }
                    availableTagsGroup.addView(this)
                }
            }

            // Update selected tags
            selectedTagsGroup.removeAllViews()
            pattern.tags.forEach { addTag(it) }

            // Update record
            pattern.record?.let { record ->
                recordCard.visibility = View.VISIBLE
                recordCatchesEdit.setText(record.catches.toString())
                recordDateEdit.setText(dateFormat.format(Date(record.date)))
            } ?: run {
                recordCard.visibility = View.GONE
            }

            // Update run history
            runAdapter.submitList(pattern.runHistory.runs)
        }
    }

    private fun addTag(tag: String) {
        // Update available tag chip state
        binding.availableTagsGroup.children.forEach { view ->
            if (view is Chip && view.text == tag) {
                view.isChecked = true
            }
        }

        // Add to selected tags if not already there
        if (binding.selectedTagsGroup.children.none { (it as Chip).text == tag }) {
            Chip(requireContext()).apply {
                text = tag
                isCloseIconVisible = true
                setOnCloseIconClickListener {
                    removeTag(tag)
                }
                binding.selectedTagsGroup.addView(this)
            }
        }
    }

    private fun removeTag(tag: String) {
        // Update available tag chip state
        binding.availableTagsGroup.children.forEach { view ->
            if (view is Chip && view.text == tag) {
                view.isChecked = false
            }
        }

        // Remove from selected tags
        binding.selectedTagsGroup.children.forEach { view ->
            if (view is Chip && view.text == tag) {
                binding.selectedTagsGroup.removeView(view)
            }
        }
    }

    private fun showDatePicker(position: Int = -1, initialDate: Long = System.currentTimeMillis(), onDateSelected: (Long) -> Unit) {
        calendar.timeInMillis = initialDate
        DatePickerDialog(
            requireContext(),
            { _, year, month, day ->
                calendar.set(year, month, day)
                onDateSelected(calendar.timeInMillis)
            },
            calendar.get(Calendar.YEAR),
            calendar.get(Calendar.MONTH),
            calendar.get(Calendar.DAY_OF_MONTH)
        ).show()
    }

    private fun showPatternSelectionDialog(title: String, onPatternSelected: (Pattern) -> Unit) {
        val dialogBinding = DialogPatternSelectionBinding.inflate(LayoutInflater.from(context))
        this.dialogBinding = dialogBinding
        
        val adapter = PatternAdapter { pattern ->
            onPatternSelected(pattern)
            dialog?.dismiss()
        }
        dialogBinding.patternsList.adapter = adapter
        
        // Set initial list
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val patterns = viewModel.availablePatterns.value
                adapter.submitList(patterns)
            } catch (e: Exception) {
                android.util.Log.e("EditPattern", "Error loading patterns: ${e.message}", e)
                dialog?.dismiss()
                MaterialAlertDialogBuilder(requireContext())
                    .setTitle(R.string.error)
                    .setMessage(R.string.error_loading_patterns)
                    .setPositiveButton(R.string.ok, null)
                    .show()
            }
        }

        // Setup search functionality
        dialogBinding.searchInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                val searchText = s?.toString()?.lowercase() ?: ""
                viewLifecycleOwner.lifecycleScope.launch {
                    val patterns = viewModel.availablePatterns.value
                    val filteredPatterns = patterns.filter { pattern ->
                        pattern.name.lowercase().contains(searchText) ||
                        pattern.tags.any { it.lowercase().contains(searchText) }
                    }
                    adapter.submitList(filteredPatterns)
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

    private fun savePattern() {
        try {
            if (!validatePattern()) return
            
            val pattern = createPatternFromInput() ?: return

            if (viewModel.isHeadCoach.value == true) {
                submitPattern(pattern)
            } else {
                showConfirmationDialog(pattern)
            }
        } catch (e: Exception) {
            showErrorDialog()
        }
    }

    private fun validatePattern(): Boolean {
        // Validate name
        val name = binding.nameEdit.text.toString().trim()
        if (name.isEmpty()) {
            binding.nameEdit.error = getString(R.string.required_field)
            return false
        }

        // Validate difficulty
        val difficultyStr = binding.difficultyEdit.text.toString().trim()
        if (difficultyStr.isNotEmpty()) {
            try {
                val diff = difficultyStr.toInt()
                if (diff !in 1..10) {
                    binding.difficultyEdit.error = getString(R.string.difficulty_range_error)
                    return false
                }
            } catch (e: NumberFormatException) {
                binding.difficultyEdit.error = getString(R.string.invalid_number)
                return false
            }
        }

        // Validate number of balls
        val numBallsStr = binding.numBallsEdit.text.toString().trim()
        if (numBallsStr.isNotEmpty()) {
            try {
                val num = numBallsStr.toInt()
                if (num <= 0) {
                    binding.numBallsEdit.error = getString(R.string.invalid_number_of_balls)
                    return false
                }
            } catch (e: NumberFormatException) {
                binding.numBallsEdit.error = getString(R.string.invalid_number)
                return false
            }
        }

        return true
    }

    private fun createPatternFromInput(): Pattern? {
        val currentPattern = viewModel.pattern.value ?: return null
        
        return currentPattern.copy(
            name = binding.nameEdit.text.toString().trim(),
            difficulty = binding.difficultyEdit.text.toString().trim(),
            num = binding.numBallsEdit.text.toString().trim(),
            siteswap = binding.siteswapEdit.text.toString().trim(),
            explanation = binding.explanationEdit.text.toString().trim(),
            gifUrl = binding.gifUrlEdit.text.toString().trim(),
            video = binding.videoUrlEdit.text.toString().trim(),
            videoStartTime = binding.videoStartTimeEdit.text?.toString()?.trim()?.let { startTime ->
                try {
                    startTime.toInt()
                } catch (e: NumberFormatException) {
                    binding.videoStartTimeEdit.error = getString(R.string.invalid_number)
                    return null
                }
            },
            videoEndTime = binding.videoEndTimeEdit.text?.toString()?.trim()?.let { endTime ->
                try {
                    endTime.toInt()
                } catch (e: NumberFormatException) {
                    binding.videoEndTimeEdit.error = getString(R.string.invalid_number)
                    return null
                }
            },
            url = binding.externalUrlEdit.text.toString().trim(),
            tags = binding.selectedTagsGroup.children.map { (it as Chip).text.toString() }.toList(),
            record = if (binding.recordCatchesEdit.text.toString().isNotEmpty()) {
                val catches = try {
                    binding.recordCatchesEdit.text.toString().toInt()
                } catch (e: NumberFormatException) {
                    binding.recordCatchesEdit.error = getString(R.string.invalid_number)
                    return null
                }
                Record(
                    catches = catches,
                    date = try {
                        dateFormat.parse(binding.recordDateEdit.text.toString())?.time
                            ?: System.currentTimeMillis()
                    } catch (e: Exception) {
                        System.currentTimeMillis()
                    }
                )
            } else null,
            runHistory = RunHistory(runAdapter.currentList)
        )
    }

    private fun submitPattern(pattern: Pattern) {
        viewModel.updatePattern(pattern)
        findNavController().navigateUp()
    }

    private fun showConfirmationDialog(pattern: Pattern) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.confirm_submit_changes)
            .setMessage(R.string.confirm_submit_message)
            .setPositiveButton(R.string.submit) { _, _ ->
                viewModel.updatePattern(pattern)
                MaterialAlertDialogBuilder(requireContext())
                    .setMessage(R.string.changes_submitted)
                    .setPositiveButton(R.string.ok) { _, _ ->
                        findNavController().navigateUp()
                    }
                    .show()
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun showErrorDialog() {
        android.util.Log.e("EditPattern", "Error saving pattern", Exception())
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.error)
            .setMessage(getString(R.string.error_saving_pattern))
            .setPositiveButton(R.string.ok, null)
            .show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        dialog?.dismiss()
        dialog = null
        dialogBinding = null
        _binding = null
    }
}