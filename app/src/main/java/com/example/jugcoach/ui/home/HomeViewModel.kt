package com.example.jugcoach.ui.home

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.jugcoach.data.entity.HistoryEntry
import com.example.jugcoach.data.repository.HistoryRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val historyRepository: HistoryRepository
) : ViewModel() {

    // LiveData for all history entries, sorted by timestamp (newest first)
    val historyEntries: LiveData<List<HistoryEntry>> = historyRepository.getAllHistoryEntries()
    
    // LiveData for history entry count
    private val _historyEntryCount = MutableLiveData<Int>()
    val historyEntryCount: LiveData<Int> = _historyEntryCount
    
    init {
        refreshHistoryEntryCount()
    }
    
    // Refresh the history entry count
    fun refreshHistoryEntryCount() {
        viewModelScope.launch {
            val count = withContext(Dispatchers.IO) {
                historyRepository.getHistoryEntryCount()
            }
            _historyEntryCount.value = count
        }
    }

    // Generate history entries for all existing runs (used in developer mode)
    // Returns information about history generation results
    data class HistoryGenerationResult(
        val entriesAdded: Int,
        val usedDirectAccess: Boolean
    )
    
    suspend fun generateHistoryEntriesForExistingRuns(): HistoryGenerationResult {
        return withContext(Dispatchers.IO) {
            val beforeCount = historyRepository.getHistoryEntryCount()
            val usedDirectAccess = historyRepository.generateHistoryEntriesForExistingRuns()
            val afterCount = historyRepository.getHistoryEntryCount()
            
            // Return the result with number of entries added and whether direct access was used
            HistoryGenerationResult(
                entriesAdded = afterCount - beforeCount,
                usedDirectAccess = usedDirectAccess
            )
        }
    }
}