package com.example.jugcoach.ui.chat

import com.example.jugcoach.data.entity.Pattern

data class PatternFilters(
    val numBalls: Set<String> = emptySet(),
    val difficultyRange: ClosedFloatingPointRange<Float> = 1f..10f,
    val minCatches: Int? = null,
    val maxCatches: Int? = null,
    val tags: Set<String> = emptySet(),
    val nameFilter: String = ""
)

data class PatternRecommendationState(
    val isVisible: Boolean = false,
    val filters: PatternFilters = PatternFilters(),
    val recommendedPattern: Pattern? = null,
    val selectedPattern: Pattern? = null
)