package com.example.jugcoach.ui.home

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.jugcoach.R
import com.example.jugcoach.data.JugCoachDatabase
import com.example.jugcoach.data.dao.CoachDao
import com.example.jugcoach.databinding.FragmentHomeBinding
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class HomeFragment : Fragment() {

    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!
    
    private val viewModel: HomeViewModel by viewModels()
    private lateinit var historyAdapter: HistoryAdapter
    
    @Inject
    lateinit var database: JugCoachDatabase

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        return binding.root
    }
    
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        setupRecyclerView()
        observeViewModel()
    }
    
    private fun setupRecyclerView() {
        // Create the adapter with the CoachDao from the database
        historyAdapter = HistoryAdapter(
            onPatternClicked = { patternId ->
                // Navigate to pattern details when an entry with a pattern is clicked
                val directions = HomeFragmentDirections.actionNavigationHomeToPatternDetailsFragment(patternId)
                findNavController().navigate(directions)
            },
            lifecycleOwner = viewLifecycleOwner,
            coachDao = database.coachDao()
        )
        
        binding.recyclerViewHistory.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = historyAdapter
        }
    }
    
    private fun observeViewModel() {
        viewModel.historyEntries.observe(viewLifecycleOwner) { entries ->
            if (entries.isNullOrEmpty()) {
                binding.textEmptyHistory.visibility = View.VISIBLE
                binding.recyclerViewHistory.visibility = View.GONE
            } else {
                binding.textEmptyHistory.visibility = View.GONE
                binding.recyclerViewHistory.visibility = View.VISIBLE
                historyAdapter.submitList(entries)
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}