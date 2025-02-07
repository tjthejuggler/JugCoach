package com.example.jugcoach.data.converter

import com.example.jugcoach.data.dto.PatternDTO
import com.example.jugcoach.data.dto.RecordDTO
import com.example.jugcoach.data.dto.TricksWrapper
import com.example.jugcoach.data.entity.Pattern
import com.example.jugcoach.data.entity.Record
import com.google.gson.Gson
import java.util.UUID

/**
 * Converts pattern data between DTO and Room entity formats
 */
object PatternConverter {
    private val gson = Gson()

    fun toEntity(dto: PatternDTO): Pattern {
        return Pattern(
            id = UUID.randomUUID().toString(),
            name = dto.name,
            difficulty = dto.difficulty,
            siteswap = dto.siteswap,
            num = dto.numberOfObjects?.toString(),
            explanation = dto.explanation,
            gifUrl = dto.gifUrl,
            video = dto.video,
            url = dto.url,
            tags = dto.tags ?: emptyList(),
            prerequisites = dto.prerequisites ?: emptyList(),
            dependents = dto.dependents ?: emptyList(),
            related = emptyList(), // Not present in DTO yet
            record = dto.record?.let { recordDto ->
                Record(
                    catches = recordDto.catches ?: 0,
                    date = recordDto.date?.let { 
                        try {
                            it.toLong()
                        } catch (e: NumberFormatException) {
                            System.currentTimeMillis()
                        }
                    } ?: System.currentTimeMillis()
                )
            }
        )
    }

    fun fromJson(json: String): PatternDTO {
        return gson.fromJson(json, PatternDTO::class.java)
    }

    fun fromJsonTricksWrapper(json: String): List<PatternDTO> {
        val wrapper = gson.fromJson(json, TricksWrapper::class.java)
        return wrapper.tricks.values.toList()
    }

    fun fromJsonArray(json: String): List<PatternDTO> {
        return gson.fromJson(json, Array<PatternDTO>::class.java).toList()
    }

    fun toJson(patterns: List<Pattern>): String {
        val dtos = patterns.map { pattern ->
            PatternDTO(
                name = pattern.name,
                difficulty = pattern.difficulty,
                siteswap = pattern.siteswap,
                numberOfObjects = pattern.num?.toIntOrNull(),
                explanation = pattern.explanation,
                gifUrl = pattern.gifUrl,
                video = pattern.video,
                url = pattern.url,
                tags = pattern.tags,
                prerequisites = pattern.prerequisites,
                dependents = pattern.dependents,
                record = pattern.record?.let { record ->
                    RecordDTO(
                        catches = record.catches,
                        date = record.date.toString()
                    )
                }
            )
        }
        return gson.toJson(dtos)
    }
}
