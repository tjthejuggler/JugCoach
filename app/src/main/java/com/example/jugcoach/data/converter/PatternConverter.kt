package com.example.jugcoach.data.converter

import com.example.jugcoach.data.dto.PatternDTO
import com.example.jugcoach.data.entity.Pattern
import com.google.gson.Gson
import com.google.gson.JsonObject
import java.util.UUID

/**
 * Converts legacy pattern data to Room entity format
 */
object PatternConverter {
    private val gson = Gson()

    fun toEntity(dto: PatternDTO): Pattern {
        // Create dynamic properties object
        val properties = JsonObject().apply {
            // Add siteswap if present
            dto.siteswap?.let { addProperty("siteswap", it) }
            // Add number of objects if present
            dto.numberOfObjects?.let { addProperty("numberOfObjects", it) }
            // Add multimedia links if present
            dto.gifUrl?.let { addProperty("gifUrl", it) }
            dto.video?.let { addProperty("video", it) }
            dto.url?.let { addProperty("url", it) }
            // Add record if present
            dto.record?.let {
                val recordObj = JsonObject()
                it.catches?.let { catches -> recordObj.addProperty("catches", catches) }
                it.date?.let { date -> recordObj.addProperty("date", date) }
                add("record", recordObj)
            }
        }

        return Pattern(
            id = UUID.randomUUID().toString(), // Generate unique ID for new patterns
            name = dto.name,
            description = dto.explanation,
            difficulty = dto.difficulty,
            tags = dto.tags ?: emptyList(),
            prerequisites = dto.prerequisites ?: emptyList(),
            dependents = dto.dependents ?: emptyList(),
            properties = gson.toJson(properties)
        )
    }

    fun fromJson(json: String): PatternDTO {
        return gson.fromJson(json, PatternDTO::class.java)
    }

    fun fromJsonArray(json: String): List<PatternDTO> {
        return gson.fromJson(json, Array<PatternDTO>::class.java).toList()
    }
}
