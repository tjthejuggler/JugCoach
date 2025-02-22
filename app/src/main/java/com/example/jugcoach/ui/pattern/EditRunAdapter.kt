package com.example.jugcoach.ui.pattern

import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.jugcoach.data.entity.Run
import com.example.jugcoach.databinding.ItemEditRunBinding
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class EditRunAdapter(
    private val onDateClick: (Int, Run) -> Unit,
    private val onDelete: (Int) -> Unit
) : ListAdapter<Run, EditRunAdapter.ViewHolder>(RunDiffCallback()) {

    private val dateFormat = SimpleDateFormat("MMM d, yyyy", Locale.getDefault())
    private val pendingChanges = mutableMapOf<Int, Run>()

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemEditRunBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val run = getItem(position)
        holder.bind(run)
    }

    fun updateRunDate(position: Int, newDate: Long) {
        val currentList = currentList.toMutableList()
        val run = currentList[position]
        currentList[position] = run.copy(date = newDate)
        submitList(currentList)
    }

    fun removeRun(position: Int) {
        val currentList = currentList.toMutableList()
        currentList.removeAt(position)
        submitList(currentList)
    }

    inner class ViewHolder(
        private val binding: ItemEditRunBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        private val catchesWatcher = object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                val position = adapterPosition
                if (position != RecyclerView.NO_POSITION) {
                    val run = getItem(position)
                    val newCatches = s?.toString()?.toIntOrNull()
                    if (run.catches != newCatches) {
                        val updatedRun = run.copy(catches = newCatches)
                        pendingChanges[position] = updatedRun
                    }
                }
            }
        }

        private val durationWatcher = object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                val position = adapterPosition
                if (position != RecyclerView.NO_POSITION) {
                    val run = pendingChanges[position] ?: getItem(position)
                    val newDuration = s?.toString()?.toLongOrNull()
                    if (run.duration != newDuration) {
                        val updatedRun = run.copy(duration = newDuration)
                        pendingChanges[position] = updatedRun
                    }
                }
            }
        }

        private fun submitPendingChanges() {
            val position = adapterPosition
            if (position != RecyclerView.NO_POSITION && pendingChanges.containsKey(position)) {
                val currentList = currentList.toMutableList()
                currentList[position] = pendingChanges[position]!!
                pendingChanges.remove(position)
                submitList(currentList)
            }
        }

        init {
            binding.catchesEdit.apply {
                addTextChangedListener(catchesWatcher)
                setOnFocusChangeListener { _, hasFocus ->
                    if (!hasFocus) {
                        submitPendingChanges()
                    }
                }
            }
            
            binding.durationEdit.apply {
                addTextChangedListener(durationWatcher)
                setOnFocusChangeListener { _, hasFocus ->
                    if (!hasFocus) {
                        submitPendingChanges()
                    }
                }
            }
            
            binding.dateEdit.setOnClickListener {
                val position = adapterPosition
                if (position != RecyclerView.NO_POSITION) {
                    submitPendingChanges() // Submit any pending changes before date selection
                    onDateClick(position, getItem(position))
                }
            }
            
            binding.cleanEndCheckbox.setOnCheckedChangeListener { _, isChecked ->
                val position = adapterPosition
                if (position != RecyclerView.NO_POSITION) {
                    val run = pendingChanges[position] ?: getItem(position)
                    pendingChanges[position] = run.copy(isCleanEnd = isChecked)
                    submitPendingChanges()
                }
            }
            
            binding.deleteButton.setOnClickListener {
                val position = adapterPosition
                if (position != RecyclerView.NO_POSITION) {
                    pendingChanges.remove(position)
                    onDelete(position)
                }
            }
        }

        fun bind(run: Run) {
            binding.apply {
                catchesEdit.setText(run.catches?.toString() ?: "")
                durationEdit.setText(run.duration?.toString() ?: "")
                dateEdit.setText(dateFormat.format(Date(run.date)))
                cleanEndCheckbox.isChecked = run.isCleanEnd
            }
        }
    }

    private class RunDiffCallback : DiffUtil.ItemCallback<Run>() {
        override fun areItemsTheSame(oldItem: Run, newItem: Run): Boolean {
            return oldItem.date == newItem.date
        }

        override fun areContentsTheSame(oldItem: Run, newItem: Run): Boolean {
            return oldItem == newItem
        }
    }
}