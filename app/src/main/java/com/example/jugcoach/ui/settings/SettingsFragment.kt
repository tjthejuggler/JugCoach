package com.example.jugcoach.ui.settings

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import com.example.jugcoach.BuildConfig
import com.example.jugcoach.databinding.FragmentSettingsBinding
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class SettingsFragment : Fragment() {
    private var _binding: FragmentSettingsBinding? = null
    private val binding get() = _binding!!
    private lateinit var viewModel: SettingsViewModel

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        viewModel = ViewModelProvider(this).get(SettingsViewModel::class.java)
        _binding = FragmentSettingsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupViews()
        observeUiState()
    }

    private fun setupViews() {
        binding.apply {
            versionText.text = "Version: ${BuildConfig.VERSION_NAME}"
            
            saveButton.setOnClickListener {
                val apiKey = apiKeyInput.text?.toString() ?: ""
                viewModel.updateApiKey(apiKey)
            }
        }
    }

    private fun observeUiState() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.uiState.collectLatest { state ->
                updateUi(state)
            }
        }
    }

    private fun updateUi(state: SettingsUiState) {
        binding.apply {
            // Only update if text is different to avoid cursor position reset
            if (apiKeyInput.text?.toString() != state.apiKey) {
                apiKeyInput.setText(state.apiKey)
            }

            patternsCountText.text = "Patterns: ${state.patternCount}"
            saveButton.isEnabled = !state.isSaving

            state.message?.let { message ->
                Snackbar.make(root, message, Snackbar.LENGTH_SHORT).show()
                viewModel.clearMessage()
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
