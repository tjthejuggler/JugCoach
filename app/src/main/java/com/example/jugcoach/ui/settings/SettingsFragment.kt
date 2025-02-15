package com.example.jugcoach.ui.settings

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import dagger.hilt.android.AndroidEntryPoint
import androidx.lifecycle.lifecycleScope
import android.content.pm.PackageManager
import com.example.jugcoach.databinding.FragmentSettingsBinding
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

@AndroidEntryPoint
class SettingsFragment : Fragment() {
    private var _binding: FragmentSettingsBinding? = null
    private val binding get() = _binding!!
    private val viewModel: SettingsViewModel by viewModels()
    private lateinit var apiKeyAdapter: ApiKeyAdapter
    
    private val getContent = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let { viewModel.importPatternsFromUri(it) }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
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
            val packageInfo = requireContext().packageManager.getPackageInfo(requireContext().packageName, 0)
            versionText.text = "Version: ${packageInfo.versionName}"

            apiKeyAdapter = ApiKeyAdapter(
                onSave = viewModel::saveApiKey,
                onDelete = viewModel::deleteApiKey
            )
            apiKeysList.adapter = apiKeyAdapter

            addApiKeyButton.setOnClickListener {
                viewModel.addApiKey()
            }

            importButton.setOnClickListener {
                getContent.launch("application/json")
            }

            // Set up model settings save functionality
            routingModelNameInput.setOnFocusChangeListener { _, hasFocus ->
                if (!hasFocus) saveModelSettings()
            }
            routingModelKeyInput.setOnFocusChangeListener { _, hasFocus ->
                if (!hasFocus) saveModelSettings()
            }
            toolUseModelNameInput.setOnFocusChangeListener { _, hasFocus ->
                if (!hasFocus) saveModelSettings()
            }
            toolUseModelKeyInput.setOnFocusChangeListener { _, hasFocus ->
                if (!hasFocus) saveModelSettings()
            }
        }
    }

    private fun saveModelSettings() {
        binding.apply {
            viewModel.saveModelSettings(
                routingModelName = routingModelNameInput.text.toString(),
                routingModelKey = routingModelKeyInput.text.toString(),
                toolUseModelName = toolUseModelNameInput.text.toString(),
                toolUseModelKey = toolUseModelKeyInput.text.toString()
            )
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
            apiKeyAdapter.submitList(state.apiKeys)
            patternsCountText.text = "Patterns: ${state.patternCount}"
            addApiKeyButton.isEnabled = !state.isSaving
            importButton.isEnabled = !state.isImporting

            // Update model settings
            if (routingModelNameInput.text.toString() != state.routingModelName) {
                routingModelNameInput.setText(state.routingModelName)
            }
            if (routingModelKeyInput.text.toString() != state.routingModelKey) {
                routingModelKeyInput.setText(state.routingModelKey)
            }
            if (toolUseModelNameInput.text.toString() != state.toolUseModelName) {
                toolUseModelNameInput.setText(state.toolUseModelName)
            }
            if (toolUseModelKeyInput.text.toString() != state.toolUseModelKey) {
                toolUseModelKeyInput.setText(state.toolUseModelKey)
            }

            // Show loading indicators
            importButton.text = if (state.isImporting) "Importing..." else "Import Patterns"

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
