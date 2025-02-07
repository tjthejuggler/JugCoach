package com.example.jugcoach.ui.gallery

data class FilterOptions(
    val numBalls: Set<String> = emptySet(),
    val difficultyRange: Pair<Float, Float> = Pair(1f, 10f),
    val tags: Set<String> = emptySet(),
    val practicedWithin: PracticedWithin? = null,
    val catchesRange: Pair<Int?, Int?> = Pair(null, null)
)

data class PracticedWithin(
    val value: Int,
    val period: Period
)

enum class Period {
    DAYS, WEEKS, MONTHS
}
