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
import com.example.jugcoach.R
import com.example.jugcoach.data.entity.Coach
import com.example.jugcoach.databinding.FragmentChatBinding
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
        setupRecyclerView()
        setupMessageInput()
        setupCoachSpinner()
        observeUiState()
    }

    private fun setupRecyclerView() {
        chatAdapter = ChatAdapter()
        binding.messagesList.apply {
            adapter = chatAdapter
            layoutManager = LinearLayoutManager(context).apply {
                stackFromEnd = true
            }
        }
    }

    private fun setupMessageInput() {
        binding.apply {
            messageInput.doAfterTextChanged { text ->
                sendButton.isEnabled = !text.isNullOrBlank()
            }

            sendButton.setOnClickListener {
                val message = messageInput.text?.toString()?.trim()
                if (!message.isNullOrEmpty()) {
                    viewModel.sendMessage(message)
                    messageInput.text?.clear()
                }
            }
        }
    }

    private fun setupCoachSpinner() {
        binding.coachSpinner.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: android.widget.AdapterView<*>?, view: View?, position: Int, id: Long) {
                val coaches = viewModel.uiState.value.availableCoaches
                if (position < coaches.size) {
                    viewModel.selectCoach(coaches[position])
                }
            }
            override fun onNothingSelected(parent: android.widget.AdapterView<*>?) {}
        }
    }

    private fun observeUiState() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.uiState.collectLatest { state ->
                updateUi(state)
            }
        }
    }

    private fun updateUi(state: ChatUiState) {
        binding.apply {
            loadingIndicator.isVisible = state.isLoading
            chatAdapter.submitList(state.messages) {
                // Scroll to bottom when new messages arrive
                if (state.messages.isNotEmpty()) {
                    messagesList.smoothScrollToPosition(state.messages.size - 1)
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
            else -> super.onOptionsItemSelected(item)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
