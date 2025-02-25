package com.example.jugcoach.ui.developer

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import com.example.jugcoach.R
import com.example.jugcoach.databinding.FragmentDeveloperBinding
import com.example.jugcoach.ui.home.HomeViewModel
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class DeveloperFragment : Fragment() {

    private var _binding: FragmentDeveloperBinding? = null
    private val binding get() = _binding!!
    
    private val viewModel: HomeViewModel by viewModels()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentDeveloperBinding.inflate(inflater, container, false)
        return binding.root
    }
    
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        binding.buttonPopulateHistory.setOnClickListener {
            populateHistoryLog()
        }
        
        // Observe history entry count
        viewModel.historyEntryCount.observe(viewLifecycleOwner) { count ->
            binding.textHistoryCount.text = getString(R.string.history_entry_count, count)
        }
    }
    
    private fun populateHistoryLog() {
        // Show loading state
        binding.progressBar.visibility = View.VISIBLE
        binding.buttonPopulateHistory.isEnabled = false
        
        // Start populating history
        lifecycleScope.launch {
            try {
                // Log the start of the operation
                android.util.Log.d("DEBUG_HISTORY", "Starting history generation from DeveloperFragment")
                val result = viewModel.generateHistoryEntriesForExistingRuns()
                android.util.Log.d("DEBUG_HISTORY", "Result: entries=${result.entriesAdded}, directAccess=${result.usedDirectAccess}")
                
                // Show appropriate success message based on results
                if (result.entriesAdded > 0) {
                    // Choose message based on whether direct access was used
                    val messageResId = if (result.usedDirectAccess) {
                        R.string.history_populated_count_direct
                    } else {
                        R.string.history_populated_count
                    }
                    
                    Toast.makeText(
                        requireContext(),
                        getString(messageResId, result.entriesAdded),
                        Toast.LENGTH_LONG
                    ).show()
                } else {
                    Toast.makeText(
                        requireContext(),
                        R.string.history_populated_no_runs,
                        Toast.LENGTH_LONG
                    ).show()
                }
            } catch (e: Exception) {
                // Log and show error message
                android.util.Log.e("DEBUG_HISTORY", "Error in populateHistoryLog", e)
                Toast.makeText(
                    requireContext(),
                    getString(R.string.history_populated_error, e.message),
                    Toast.LENGTH_LONG
                ).show()
            } finally {
                // Hide loading state
                binding.progressBar.visibility = View.GONE
                binding.buttonPopulateHistory.isEnabled = true
                
                // Refresh history count
                viewModel.refreshHistoryEntryCount()
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}