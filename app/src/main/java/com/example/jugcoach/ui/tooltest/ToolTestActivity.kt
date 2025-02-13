package com.example.jugcoach.ui.tooltest

import android.os.Bundle
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.widget.doAfterTextChanged
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.example.jugcoach.databinding.ActivityToolTestBinding
import com.google.android.material.snackbar.Snackbar
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class ToolTestActivity : AppCompatActivity() {

    private val viewModel: ToolTestViewModel by viewModels()
    private lateinit var binding: ActivityToolTestBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityToolTestBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupViews()
        observeState()
    }

    private fun setupViews() {
        // Pattern lookup
        binding.editTextArgument.doAfterTextChanged { text ->
            viewModel.setPatternId(text?.toString() ?: "")
        }

        binding.buttonSendToolRequest.setOnClickListener {
            viewModel.testLookupPattern()
        }

        // Pattern search
        binding.editTextSearch.doAfterTextChanged { text ->
            viewModel.setSearchCriteria(text?.toString() ?: "")
        }

        binding.buttonSearch.setOnClickListener {
            viewModel.testSearchPatterns()
        }
    }

    private fun observeState() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    // Update response text
                    binding.textViewToolResponse.text = state.response

                    // Update loading states
                    val isLoading = state.isLoading
                    binding.buttonSendToolRequest.isEnabled = !isLoading
                    binding.buttonSearch.isEnabled = !isLoading

                    // Show error if any
                    state.error?.let { error ->
                        Snackbar.make(binding.root, error, Snackbar.LENGTH_LONG).show()
                        viewModel.clearError()
                    }
                }
            }
        }
    }
}
