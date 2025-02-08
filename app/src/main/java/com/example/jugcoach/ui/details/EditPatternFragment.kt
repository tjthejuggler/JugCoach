package com.example.jugcoach.ui.details

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
    private val viewModel: PatternDetailsViewModel by viewModels()

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
        setupTagInput()
        setupPatternSelectionButtons()
        observePattern()
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

    private fun setupTagInput() {
        binding.addTagLayout.setEndIconOnClickListener {
            val tagText = binding.addTagEdit.text?.toString()?.trim()
            if (!tagText.isNullOrEmpty()) {
                addTag(tagText)
                binding.addTagEdit.text?.clear()
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

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.availablePatterns.collect { patterns ->
                    // Update the dialog adapter if it's showing
                    val currentDialog = dialog
                    val currentBinding = dialogBinding
                    if (currentDialog?.isShowing == true && currentBinding != null) {
                        val searchText = currentBinding.searchEdit.text?.toString()?.lowercase() ?: ""
                        val filteredPatterns = patterns.filter { pattern ->
                            pattern.name.lowercase().contains(searchText) ||
                            pattern.tags.any { it.lowercase().contains(searchText) }
                        }
                        (currentBinding.patternsList.adapter as? PatternAdapter)?.submitList(filteredPatterns)
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
            externalUrlEdit.setText(pattern.url)

            // Update tags
            tagsGroup.removeAllViews()
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
        Chip(requireContext()).apply {
            text = tag
            isCloseIconVisible = true
            setOnCloseIconClickListener {
                binding.tagsGroup.removeView(this)
            }
            binding.tagsGroup.addView(this)
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
        dialogBinding.searchEdit.addTextChangedListener(object : TextWatcher {
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
            // Validate required fields
            val name = binding.nameEdit.text.toString().trim()
            if (name.isEmpty()) {
                binding.nameEdit.error = getString(R.string.required_field)
                return
            }

            // Validate difficulty
            val difficultyStr = binding.difficultyEdit.text.toString().trim()
            val difficulty = if (difficultyStr.isNotEmpty()) {
                try {
                    val diff = difficultyStr.toInt()
                    if (diff !in 1..10) {
                        binding.difficultyEdit.error = getString(R.string.difficulty_range_error)
                        return
                    }
                    difficultyStr
                } catch (e: NumberFormatException) {
                    binding.difficultyEdit.error = getString(R.string.invalid_number)
                    return
                }
            } else null

            // Validate number of balls
            val numBallsStr = binding.numBallsEdit.text.toString().trim()
            val numBalls = if (numBallsStr.isNotEmpty()) {
                try {
                    val num = numBallsStr.toInt()
                    if (num <= 0) {
                        binding.numBallsEdit.error = getString(R.string.invalid_number_of_balls)
                        return
                    }
                    numBallsStr
                } catch (e: NumberFormatException) {
                    binding.numBallsEdit.error = getString(R.string.invalid_number)
                    return
                }
            } else null

            val pattern = viewModel.pattern.value?.copy(
                name = name,
                difficulty = difficulty,
                num = numBalls,
                siteswap = binding.siteswapEdit.text.toString().trim(),
                explanation = binding.explanationEdit.text.toString().trim(),
                gifUrl = binding.gifUrlEdit.text.toString().trim(),
                video = binding.videoUrlEdit.text.toString().trim(),
                url = binding.externalUrlEdit.text.toString().trim(),
                tags = binding.tagsGroup.children.map { (it as Chip).text.toString() }.toList(),
                record = if (binding.recordCatchesEdit.text.toString().isNotEmpty()) {
                    val catches = try {
                        binding.recordCatchesEdit.text.toString().toInt()
                    } catch (e: NumberFormatException) {
                        binding.recordCatchesEdit.error = getString(R.string.invalid_number)
                        return
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
            
            pattern?.let {
                viewModel.updatePattern(it)
                findNavController().navigateUp()
            }
        } catch (e: Exception) {
            android.util.Log.e("EditPattern", "Error saving pattern: ${e.message}", e)
            MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.error)
                .setMessage(getString(R.string.error_saving_pattern))
                .setPositiveButton(R.string.ok, null)
                .show()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        dialog?.dismiss()
        dialog = null
        dialogBinding = null
        _binding = null
    }
}
