package com.example.jugcoach.ui.coach

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import com.example.jugcoach.databinding.FragmentCreateCoachBinding
import com.google.android.material.snackbar.Snackbar
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import androidx.lifecycle.lifecycleScope

@AndroidEntryPoint
class CreateCoachFragment : Fragment() {
    private var _binding: FragmentCreateCoachBinding? = null
    private val binding get() = _binding!!
    private val viewModel: CreateCoachViewModel by viewModels()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentCreateCoachBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupViews()
        observeApiKeys()
    }

    private fun setupViews() {
        binding.apply {
            createButton.setOnClickListener {
                val name = nameInput.text.toString()
                val apiKey = apiKeyDropdown.text.toString()
                val description = descriptionInput.text.toString().takeIf { it.isNotBlank() }
                val specialties = specialtiesInput.text.toString().takeIf { it.isNotBlank() }

                if (name.isBlank()) {
                    Snackbar.make(root, "Please enter a name", Snackbar.LENGTH_SHORT).show()
                    return@setOnClickListener
                }

                if (apiKey.isBlank()) {
                    Snackbar.make(root, "Please select an API key", Snackbar.LENGTH_SHORT).show()
                    return@setOnClickListener
                }

                viewModel.createCoach(name, apiKey, description, specialties)
                findNavController().navigateUp()
            }
        }
    }

    private fun observeApiKeys() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.apiKeys.collectLatest { apiKeys ->
                val adapter = ArrayAdapter(
                    requireContext(),
                    android.R.layout.simple_dropdown_item_1line,
                    apiKeys
                )
                binding.apiKeyDropdown.setAdapter(adapter)
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
