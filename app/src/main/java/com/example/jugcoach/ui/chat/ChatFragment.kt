package com.example.jugcoach.ui.chat

import android.os.Bundle
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import androidx.core.view.isVisible
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.jugcoach.R
import com.example.jugcoach.data.entity.Coach
import com.example.jugcoach.databinding.FragmentChatBinding
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

@AndroidEntryPoint
class ChatFragment : Fragment() {
    private var _binding: FragmentChatBinding? = null
    private val binding get() = _binding!!
    private val viewModel: ChatViewModel by viewModels()
    private lateinit var chatAdapter: ChatAdapter
    private lateinit var layoutManager: LinearLayoutManager

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentChatBinding.inflate(inflater, container, false)
        setHasOptionsMenu(true)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        android.util.Log.d("ChatFragment", "Setting up fragment")
        setupRecyclerView()
        setupMessageInput()
        setupCoachSpinner()
        observeUiState()
        observeApiKeys()
    }

    private fun observeApiKeys() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.availableApiKeys.collect { apiKeys ->
                android.util.Log.d("ChatFragment", "Available API keys: $apiKeys")
            }
        }
    }

    private fun setupRecyclerView() {
        android.util.Log.d("ChatFragment", "Setting up RecyclerView")
        chatAdapter = ChatAdapter()
        layoutManager = LinearLayoutManager(requireContext()).apply {
            stackFromEnd = true
            reverseLayout = false
        }

        binding.messagesList.apply {
            setHasFixedSize(true)
            itemAnimator = null // Disable animations to prevent flickering
            layoutManager = this@ChatFragment.layoutManager
            adapter = chatAdapter

            // Add scroll listener for debugging
            addOnScrollListener(object : RecyclerView.OnScrollListener() {
                override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                    (layoutManager as? LinearLayoutManager)?.let { manager ->
                        val firstVisible = manager.findFirstVisibleItemPosition()
                        val lastVisible = manager.findLastVisibleItemPosition()
                        android.util.Log.d("ChatFragment", "Scroll - First: $firstVisible, Last: $lastVisible")
                    }
                }
            })
        }
        android.util.Log.d("ChatFragment", "RecyclerView setup complete")
    }

    private fun setupMessageInput() {
        android.util.Log.d("ChatFragment", "Setting up message input")
        binding.apply {
            messageInput.doAfterTextChanged { text ->
                sendButton.isEnabled = !text.isNullOrBlank()
            }

            sendButton.setOnClickListener {
                val message = messageInput.text?.toString()?.trim()
                if (!message.isNullOrEmpty()) {
                    android.util.Log.d("ChatFragment", "Sending message: $message")
                    viewModel.sendMessage(message)
                    messageInput.text?.clear()
                }
            }
        }
    }

    private fun setupCoachSpinner() {
        android.util.Log.d("ChatFragment", "Setting up coach spinner")
        binding.coachSpinner.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: android.widget.AdapterView<*>?, view: View?, position: Int, id: Long) {
                val coaches = viewModel.uiState.value.availableCoaches
                if (position < coaches.size) {
                    android.util.Log.d("ChatFragment", "Selected coach: ${coaches[position].name}")
                    viewModel.selectCoach(coaches[position])
                }
            }
            override fun onNothingSelected(parent: android.widget.AdapterView<*>?) {}
        }
    }

    private fun observeUiState() {
        android.util.Log.d("ChatFragment", "Starting UI state observation")
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.uiState.collectLatest { state ->
                updateUi(state)
            }
        }
    }

    private fun updateUi(state: ChatUiState) {
        android.util.Log.d("ChatFragment", "Updating UI with state: loading=${state.isLoading}, messages=${state.messages.size}")
        binding.apply {
            loadingIndicator.isVisible = state.isLoading
            
            // Log each message for debugging
            state.messages.forEachIndexed { index, msg ->
                android.util.Log.d("ChatFragment", "Message $index - ${msg.sender}: ${msg.text}")
            }
            
            // Create a new list to force DiffUtil to run
            val newList = state.messages.toList()
            android.util.Log.d("ChatFragment", "Submitting new list with ${newList.size} messages")
            chatAdapter.submitList(newList) {
                // Scroll to bottom when new messages arrive
                if (newList.isNotEmpty()) {
                    android.util.Log.d("ChatFragment", "Scrolling to position: ${newList.size - 1}")
                    messagesList.post {
                        try {
                            messagesList.smoothScrollToPosition(newList.size - 1)
                        } catch (e: Exception) {
                            android.util.Log.e("ChatFragment", "Error scrolling", e)
                        }
                    }
                }
            }

            // Update coach spinner
            val adapter = ArrayAdapter(
                requireContext(),
                android.R.layout.simple_spinner_item,
                state.availableCoaches.map { it.name }
            ).apply {
                setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            }
            coachSpinner.adapter = adapter
            state.currentCoach?.let { currentCoach ->
                val index = state.availableCoaches.indexOfFirst { it.id == currentCoach.id }
                if (index >= 0) {
                    coachSpinner.setSelection(index)
                }
            }

            state.error?.let { error ->
                android.util.Log.e("ChatFragment", "Showing error: $error")
                Snackbar.make(root, error, Snackbar.LENGTH_LONG).show()
            }
        }
    }

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        inflater.inflate(R.menu.chat_menu, menu)
        super.onCreateOptionsMenu(menu, inflater)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_create_coach -> {
                findNavController().navigate(R.id.action_nav_chat_to_createCoachFragment)
                true
            }
            R.id.action_change_api_key -> {
                showApiKeySelectionDialog()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun showApiKeySelectionDialog() {
        viewLifecycleOwner.lifecycleScope.launch {
            val apiKeys = viewModel.availableApiKeys.value
            val currentCoach = viewModel.uiState.value.currentCoach
            
            if (apiKeys.isEmpty()) {
                Snackbar.make(binding.root, "No API keys available. Add them in Settings first.", Snackbar.LENGTH_LONG).show()
                return@launch
            }

            val currentKeyIndex = apiKeys.indexOf(currentCoach?.apiKeyName)
            
            // Convert API key names to display names
            val displayNames = apiKeys.map { key -> 
                key.removePrefix("llm_api_key_").let { name ->
                    if (name.isBlank()) "Default API Key" else "API Key ${name}"
                }
            }.toTypedArray()

            MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.select_api_key)
                .setSingleChoiceItems(
                    displayNames,
                    currentKeyIndex
                ) { dialog, which ->
                    viewModel.updateCoachApiKey(apiKeys[which])
                    dialog.dismiss()
                }
                .setNegativeButton(R.string.cancel, null)
                .show()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
