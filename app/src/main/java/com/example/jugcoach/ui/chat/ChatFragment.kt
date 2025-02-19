package com.example.jugcoach.ui.chat

import android.os.Bundle
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.PopupMenu
import com.google.android.material.textfield.TextInputEditText
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
import com.example.jugcoach.data.entity.Pattern
import com.example.jugcoach.databinding.FragmentChatBinding
import com.example.jugcoach.ui.chat.PatternRecommendationBottomSheet
import com.example.jugcoach.ui.gallery.CreatePatternBottomSheetFragment
import com.example.jugcoach.ui.gallery.CreatePatternViewModel
import com.example.jugcoach.ui.adapters.PatternRunAdapter
import com.example.jugcoach.ui.chat.PatternRelationships
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
    private var patternRunAdapter: PatternRunAdapter? = null
    
    // Flags to prevent spinner selection callbacks during UI updates
    private var isUpdatingCoachSpinner = false
    private var isUpdatingConversationSpinner = false

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
        setupConversationControls()
        observeUiState()
        observeApiKeys()
    }

    private fun setupConversationControls() {
        binding.apply {
            // Setup conversation spinner
            conversationSpinner.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
                override fun onItemSelected(parent: android.widget.AdapterView<*>?, view: View?, position: Int, id: Long) {
                    if (isUpdatingConversationSpinner) return
                    val conversations = viewModel.uiState.value.availableConversations
                    if (position < conversations.size) {
                        viewModel.selectConversation(conversations[position])
                    }
                }
                override fun onNothingSelected(parent: android.widget.AdapterView<*>?) {}
            }

            // Setup new conversation button
            newConversationButton.setOnClickListener {
                viewModel.createNewConversation()
            }

            // Long press on conversation spinner to rename
            conversationSpinner.setOnLongClickListener {
                showRenameConversationDialog()
                true
            }
        }
    }

    private fun showRenameConversationDialog() {
        val currentConversation = viewModel.uiState.value.currentConversation ?: return
        val editText = com.google.android.material.textfield.TextInputEditText(requireContext()).apply {
            setText(currentConversation.title)
            setSingleLine()
        }

        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.rename_conversation)
            .setView(editText)
            .setPositiveButton(R.string.save) { _, _ ->
                val newTitle = editText.text?.toString()?.takeIf { it.isNotBlank() }
                if (newTitle != null) {
                    viewModel.updateConversationTitle(newTitle)
                }
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
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
        chatAdapter = ChatAdapter(
            currentCoach = viewModel.uiState.value.currentCoach,
            onAgainClick = { message -> viewModel.startRunFromMessage(message) },
            onDifferentClick = { message ->
                viewLifecycleOwner.lifecycleScope.launch {
                    val pattern = viewModel.findPatternByName(message.text.lines().first())?.let { pattern ->
                        showDifferentPatternMenu(pattern)
                    }
                }
            },
            onCreateClick = { message, view ->
                viewLifecycleOwner.lifecycleScope.launch {
                    val pattern = viewModel.findPatternByName(message.text.lines().first())?.let { pattern ->
                        showCreatePatternMenu(pattern, view)
                    }
                }
            },
            onPatternClick = { patternName ->
                viewLifecycleOwner.lifecycleScope.launch {
                    viewModel.findPatternByName(patternName)?.let { pattern ->
                        findNavController().navigate(
                            R.id.action_nav_chat_to_patternDetailsFragment,
                            Bundle().apply {
                                putString("patternId", pattern.id)
                            }
                        )
                    }
                }
            }
        ).apply {
            setHasStableIds(true)  // Enable stable IDs for better performance
        }
        layoutManager = LinearLayoutManager(requireContext()).apply {
            stackFromEnd = true
            reverseLayout = false
        }

        binding.messagesList.apply {
            adapter = chatAdapter
            layoutManager = this@ChatFragment.layoutManager
            
            // Optimize RecyclerView settings
            setHasFixedSize(true)
            itemAnimator = null  // Disable animations completely
            setItemViewCacheSize(20)  // Increase view cache
            
            // Prevent unnecessary processing
            isNestedScrollingEnabled = false
            overScrollMode = View.OVER_SCROLL_NEVER
            
            // Use hardware acceleration
            setLayerType(View.LAYER_TYPE_HARDWARE, null)
        }
        android.util.Log.d("ChatFragment", "RecyclerView setup complete")
    }

    private fun showPatternRecommendation() {
        PatternRecommendationBottomSheet.newInstance()
            .show(childFragmentManager, PatternRecommendationBottomSheet.TAG)
        viewModel.showPatternRecommendation()
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

            patternRecommendationButton.setOnClickListener {
                showPatternRecommendation()
            }
        }
    }

    private fun setupCoachSpinner() {
        android.util.Log.d("ChatFragment", "Setting up coach spinner")
        binding.coachSpinner.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: android.widget.AdapterView<*>?, view: View?, position: Int, id: Long) {
                if (isUpdatingCoachSpinner) return
                val coaches = viewModel.uiState.value.availableCoaches
                if (position < coaches.size) {
                    viewModel.selectCoach(coaches[position])
                }
            }
            override fun onNothingSelected(parent: android.widget.AdapterView<*>?) {}
        }
    }

    private fun observeUiState() {
        android.util.Log.d("ChatFragment", "Starting UI state observation")
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.uiState.collect { state ->
                updateUi(state)
            }
        }
    }

    private fun updateUi(state: ChatUiState) {
        binding.apply {
            loadingIndicator.isVisible = state.isLoading

            // Handle pattern run state
            state.patternRun?.let { runState ->
                android.util.Log.d("TimerDebug", "Setting up pattern run view")
                patternRunView.visibility = View.VISIBLE
                android.util.Log.d("TimerDebug", "Pattern run view visibility set to VISIBLE")
                
                // Set layout manager if not already set
                if (patternRunView.layoutManager == null) {
                    android.util.Log.d("TimerDebug", "Setting LinearLayoutManager for pattern run view")
                    patternRunView.layoutManager = LinearLayoutManager(requireContext())
                }
                
                // Create pattern run adapter if it doesn't exist
                if (patternRunAdapter == null) {
                    android.util.Log.d("TimerDebug", "Creating new pattern run adapter")
                    android.util.Log.d("PatternDebug", "Creating new PatternRunAdapter")
                    patternRunAdapter = PatternRunAdapter.create(
                        onStartTimer = {
                            android.util.Log.d("PatternDebug", "Start timer clicked")
                            viewModel.startTimer()
                        },
                        onEndRun = { wasCatch -> showEndRunDialog(wasCatch) },
                        onPatternClick = { pattern ->
                            findNavController().navigate(
                                R.id.action_nav_chat_to_patternDetailsFragment,
                                Bundle().apply {
                                    putString("patternId", pattern.id)
                                }
                            )
                        },
                        onClose = { viewModel.cancelPatternRun() }
                    )
                }
                // Always ensure adapter is set and bind the current state
                patternRunView.adapter = patternRunAdapter
                patternRunAdapter?.bind(runState)
            } ?: run {
                patternRunView.visibility = View.GONE
            }
            
            // Refresh options menu when current conversation changes
            requireActivity().invalidateOptionsMenu()

            // Update conversation spinner
            isUpdatingConversationSpinner = true
            val conversationAdapter = ArrayAdapter(
                requireContext(),
                android.R.layout.simple_spinner_item,
                state.availableConversations.map { conversation ->
                    conversation.title ?: "Chat ${java.time.format.DateTimeFormatter.ISO_INSTANT.format(java.time.Instant.ofEpochMilli(conversation.createdAt))}"
                }
            ).apply {
                setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            }
            conversationSpinner.adapter = conversationAdapter
            state.currentConversation?.let { currentConversation ->
                val index = state.availableConversations.indexOfFirst { it.id == currentConversation.id }
                if (index >= 0) {
                    conversationSpinner.setSelection(index)
                }
            }
            isUpdatingConversationSpinner = false
            
            // Update coach in adapter
            chatAdapter.updateCoach(state.currentCoach)

            // Update messages with distinct check
            val currentList = chatAdapter.currentList
            if (state.messages != currentList) {
                chatAdapter.submitList(state.messages) {
                    // Scroll to bottom only when new messages are added
                    if (state.messages.size > currentList.size) {
                        messagesList.post {
                            messagesList.scrollToPosition(state.messages.size - 1)
                        }
                    }
                }
            }

            // Update coach spinner
            isUpdatingCoachSpinner = true
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
            isUpdatingCoachSpinner = false

            state.error?.let { error ->
                android.util.Log.e("ChatFragment", "Showing error: $error")
                Snackbar.make(root, error, Snackbar.LENGTH_LONG).show()
            }
        }
    }

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        inflater.inflate(R.menu.chat_menu, menu)
        
        // Update conversation-related menu items based on current conversation
        viewModel.uiState.value.currentConversation?.let { conversation ->
            // Show and update favorite icon
            menu.findItem(R.id.action_favorite_conversation)?.apply {
                setIcon(if (conversation.isFavorite) android.R.drawable.btn_star_big_on else android.R.drawable.btn_star_big_off)
                setTitle(if (conversation.isFavorite) R.string.unfavorite_conversation else R.string.favorite_conversation)
                isVisible = true
            }
            // Show rename option
            menu.findItem(R.id.action_rename_conversation)?.isVisible = true
        } ?: run {
            // Hide conversation-related actions when no conversation is selected
            menu.findItem(R.id.action_favorite_conversation)?.isVisible = false
            menu.findItem(R.id.action_rename_conversation)?.isVisible = false
        }
        
        super.onCreateOptionsMenu(menu, inflater)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_rename_conversation -> {
                showRenameConversationDialog()
                true
            }
            R.id.action_favorite_conversation -> {
                viewModel.toggleConversationFavorite()
                requireActivity().invalidateOptionsMenu() // Refresh menu to update icon
                true
            }
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

    private fun showEndRunDialog(wasCatch: Boolean) {
        // Stop the timer immediately when dialog is shown
        viewModel.stopTimer()
        
        val dialogView = LayoutInflater.from(requireContext())
            .inflate(R.layout.dialog_end_run, null)
        
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(if (wasCatch) "End Run (Catch)" else "End Run (Drop)")
            .setView(dialogView)
            .setPositiveButton(R.string.save_run) { _, _ ->
                val catchesInput = dialogView.findViewById<TextInputEditText>(R.id.catches_input)
                val catches = catchesInput.text?.toString()?.toIntOrNull()
                viewModel.endPatternRun(wasCatch, catches)
            }
            .setNegativeButton(R.string.cancel_run, null)
            .show()
    }

    private fun showDifferentPatternMenu(pattern: Pattern) {
        val popupMenu = PopupMenu(requireContext(), binding.patternRunView)
        popupMenu.menuInflater.inflate(R.menu.pattern_relationship_menu, popupMenu.menu)

        popupMenu.setOnMenuItemClickListener { menuItem ->
            viewLifecycleOwner.lifecycleScope.launch {
                val relationType = when (menuItem.itemId) {
                    R.id.menu_prerequisites -> PatternRelationships.PREREQS
                    R.id.menu_related -> PatternRelationships.RELATED
                    R.id.menu_dependent -> PatternRelationships.DEPENDENTS
                    else -> return@launch
                }

                android.util.Log.d("PatternDebug", "Getting random pattern for relationship: $relationType")
                viewModel.getRandomPatternFromRelationship(
                    pattern.id,
                    relationType
                )?.let { randomPattern ->
                    android.util.Log.d("PatternDebug", "Starting pattern run for: ${randomPattern.name}")
                    viewModel.startPatternRun(randomPattern)
                } ?: run {
                    android.util.Log.d("PatternDebug", "No patterns available, showing snackbar")
                    Snackbar.make(binding.root, R.string.no_patterns_available, Snackbar.LENGTH_SHORT).show()
                }
            }
            true
        }
        popupMenu.show()
    }

    private fun showCreatePatternMenu(pattern: Pattern, anchor: View) {
        val popupMenu = PopupMenu(requireContext(), anchor)
        popupMenu.menuInflater.inflate(R.menu.pattern_relationship_menu, popupMenu.menu)

        popupMenu.setOnMenuItemClickListener { menuItem ->
            val relationshipType = when (menuItem.itemId) {
                R.id.menu_prerequisites -> CreatePatternViewModel.RELATIONSHIP_PREREQUISITE
                R.id.menu_related -> CreatePatternViewModel.RELATIONSHIP_RELATED
                R.id.menu_dependent -> CreatePatternViewModel.RELATIONSHIP_DEPENDENT
                else -> return@setOnMenuItemClickListener false
            }

            findNavController().navigate(
                R.id.action_global_createPatternFragment,
                Bundle().apply {
                    putString("sourcePatternId", pattern.id)
                    putString("relationshipType", relationshipType)
                }
            )
            true
        }
        popupMenu.show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
