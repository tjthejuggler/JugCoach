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
    private val runs = mutableListOf<Run>()

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
                    val currentList = currentList.toMutableList()
                    val run = currentList[position]
                    currentList[position] = run.copy(
                        catches = s.toString().toIntOrNull()
                    )
                    submitList(currentList)
                }
            }
        }

        private val durationWatcher = object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                val position = adapterPosition
                if (position != RecyclerView.NO_POSITION) {
                    val currentList = currentList.toMutableList()
                    val run = currentList[position]
                    currentList[position] = run.copy(
                        duration = s.toString().toLongOrNull()
                    )
                    submitList(currentList)
                }
            }
        }

        init {
            binding.catchesEdit.addTextChangedListener(catchesWatcher)
            binding.durationEdit.addTextChangedListener(durationWatcher)
            binding.dateEdit.setOnClickListener {
                val position = adapterPosition
                if (position != RecyclerView.NO_POSITION) {
                    onDateClick(position, getItem(position))
                }
            }
            binding.cleanEndCheckbox.setOnCheckedChangeListener { _, isChecked ->
                val position = adapterPosition
                if (position != RecyclerView.NO_POSITION) {
                    val currentList = currentList.toMutableList()
                    val run = currentList[position]
                    currentList[position] = run.copy(isCleanEnd = isChecked)
                    submitList(currentList)
                }
            }
            binding.deleteButton.setOnClickListener {
                val position = adapterPosition
                if (position != RecyclerView.NO_POSITION) {
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