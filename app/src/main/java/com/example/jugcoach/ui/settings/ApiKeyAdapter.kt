package com.example.jugcoach.ui.settings

import android.view.LayoutInflater
import android.view.ViewGroup
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
        holder.bind(getItem(position))
    }

    inner class ViewHolder(
        private val binding: ItemApiKeyBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(item: ApiKeyItem) {
            binding.apply {
                keyNameInput.setText(item.name)
                apiKeyInput.setText(item.value)

                saveButton.setOnClickListener {
                    val name = keyNameInput.text.toString()
                    val key = apiKeyInput.text.toString()
                    if (name.isNotBlank() && key.isNotBlank()) {
                        onSave(name, key)
                    }
                }

                deleteButton.setOnClickListener {
                    onDelete(item.name)
                }
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
