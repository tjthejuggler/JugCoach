package com.example.jugcoach.ui.settings

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.widget.doAfterTextChanged
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.jugcoach.data.entity.Settings
import com.example.jugcoach.databinding.ItemApiKeyBinding

class ApiKeyAdapter(
    private val onSave: (String, String) -> Unit,
    private val onDelete: (String) -> Unit
) : ListAdapter<ApiKeyItem, ApiKeyAdapter.ViewHolder>(ApiKeyDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemApiKeyBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        android.util.Log.d("ApiKeyAdapter", "Binding item at position $position")
        holder.bind(getItem(position))
    }

    inner class ViewHolder(
        private val binding: ItemApiKeyBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        init {
            binding.apiKeyInput.doAfterTextChanged { text ->
                validateApiKey(text?.toString())
            }
        }

        private fun validateApiKey(key: String?) {
            android.util.Log.d("ApiKeyAdapter", "Validating API key: ${key?.take(4)}...")
            binding.apiKeyLayout.error = if (key.isNullOrBlank()) "API key is required" else null
            binding.saveButton.isEnabled = binding.apiKeyLayout.error == null && !key.isNullOrBlank()
        }

        fun bind(item: ApiKeyItem) {
            android.util.Log.d("ApiKeyAdapter", "Binding API key item: ${item.name}")
            binding.apply {
                keyNameInput.setText(item.name)
                apiKeyInput.setText(item.value)

                // Validate initial value
                validateApiKey(item.value)

                saveButton.setOnClickListener {
                    val name = keyNameInput.text.toString()
                    val key = apiKeyInput.text.toString()
                    if (name.isNotBlank() && key.isNotBlank() && binding.apiKeyLayout.error == null) {
                        android.util.Log.d("ApiKeyAdapter", "Saving API key: $name")
                        onSave(name, key)
                    }
                }

                deleteButton.setOnClickListener {
                    android.util.Log.d("ApiKeyAdapter", "Deleting API key: ${item.name}")
                    onDelete(item.name)
                }

                // Disable save button if key is empty
                saveButton.isEnabled = item.value.isNotBlank() && binding.apiKeyLayout.error == null
            }
        }
    }
}

class ApiKeyDiffCallback : DiffUtil.ItemCallback<ApiKeyItem>() {
    override fun areItemsTheSame(oldItem: ApiKeyItem, newItem: ApiKeyItem): Boolean {
        return oldItem.name == newItem.name
    }

    override fun areContentsTheSame(oldItem: ApiKeyItem, newItem: ApiKeyItem): Boolean {
        return oldItem == newItem
    }
}
