package com.example.jugcoach.ui.settings

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.Button
import androidx.activity.result.contract.ActivityResultContracts
import com.example.jugcoach.R
import com.google.android.material.dialog.MaterialAlertDialogBuilder
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

            deleteAllPatternsButton.setOnClickListener {
                showDeleteConfirmationDialog()
            }

            // Set up model settings save functionality
            routingModelNameInput.setOnFocusChangeListener { _, hasFocus ->
                if (!hasFocus) saveModelSettings()
            }
            toolUseModelNameInput.setOnFocusChangeListener { _, hasFocus ->
                if (!hasFocus) saveModelSettings()
            }

            // Set up API key dropdowns
            setupApiKeyDropdowns()
        }
    }

    private fun setupApiKeyDropdowns() {
        binding.apply {
            val routingKeyAdapter = ArrayAdapter(
                requireContext(),
                android.R.layout.simple_dropdown_item_1line,
                mutableListOf<String>()
            )
            val toolUseKeyAdapter = ArrayAdapter(
                requireContext(),
                android.R.layout.simple_dropdown_item_1line,
                mutableListOf<String>()
            )

            (routingModelKeyInput as? AutoCompleteTextView)?.setAdapter(routingKeyAdapter)
            (toolUseModelKeyInput as? AutoCompleteTextView)?.setAdapter(toolUseKeyAdapter)

            // Update adapters when API keys change
            viewLifecycleOwner.lifecycleScope.launch {
                viewModel.uiState.collectLatest { state ->
                    val apiKeyNames = state.apiKeys.map { it.name }
                    routingKeyAdapter.clear()
                    routingKeyAdapter.addAll(apiKeyNames)
                    toolUseKeyAdapter.clear()
                    toolUseKeyAdapter.addAll(apiKeyNames)

                    // Set current values
                    if (state.routingModelKey.isNotEmpty()) {
                        (routingModelKeyInput as? AutoCompleteTextView)?.setText(
                            state.apiKeys.find { it.value == state.routingModelKey }?.name ?: "",
                            false
                        )
                    }
                    if (state.toolUseModelKey.isNotEmpty()) {
                        (toolUseModelKeyInput as? AutoCompleteTextView)?.setText(
                            state.apiKeys.find { it.value == state.toolUseModelKey }?.name ?: "",
                            false
                        )
                    }
                }
            }

            // Handle selection changes
            (routingModelKeyInput as? AutoCompleteTextView)?.setOnItemClickListener { _, _, position, _ ->
                val selectedName = routingKeyAdapter.getItem(position) as String
                val selectedKey = viewModel.uiState.value.apiKeys.find { it.name == selectedName }?.value ?: ""
                saveModelSettings(routingModelKey = selectedKey)
            }

            (toolUseModelKeyInput as? AutoCompleteTextView)?.setOnItemClickListener { _, _, position, _ ->
                val selectedName = toolUseKeyAdapter.getItem(position) as String
                val selectedKey = viewModel.uiState.value.apiKeys.find { it.name == selectedName }?.value ?: ""
                saveModelSettings(toolUseModelKey = selectedKey)
            }
        }
    }

    private fun saveModelSettings(
        routingModelKey: String? = null,
        toolUseModelKey: String? = null
    ) {
        binding.apply {
            viewModel.saveModelSettings(
                routingModelName = routingModelNameInput.text.toString(),
                routingModelKey = routingModelKey ?: viewModel.uiState.value.routingModelKey,
                toolUseModelName = toolUseModelNameInput.text.toString(),
                toolUseModelKey = toolUseModelKey ?: viewModel.uiState.value.toolUseModelKey
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

            // Update model names
            if (routingModelNameInput.text.toString() != state.routingModelName) {
                routingModelNameInput.setText(state.routingModelName)
            }
            if (toolUseModelNameInput.text.toString() != state.toolUseModelName) {
                toolUseModelNameInput.setText(state.toolUseModelName)
            }

            // Show loading indicators
            importButton.text = if (state.isImporting) "Importing..." else "Import Patterns"

            state.message?.let { message ->
                Snackbar.make(root, message, Snackbar.LENGTH_SHORT).show()
                viewModel.clearMessage()
            }
        }
    }

    private fun showDeleteConfirmationDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_delete_patterns, null)
        val dialog = MaterialAlertDialogBuilder(requireContext())
            .setView(dialogView)
            .create()

        dialogView.findViewById<Button>(R.id.cancelButton).setOnClickListener {
            dialog.dismiss()
        }

        dialogView.findViewById<Button>(R.id.deleteButton).setOnClickListener {
            viewModel.deleteAllPatterns()
            dialog.dismiss()
        }

        dialog.show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
