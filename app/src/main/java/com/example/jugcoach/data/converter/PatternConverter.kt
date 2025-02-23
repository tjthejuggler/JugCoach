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
    private val nameToIdMap = mutableMapOf<String, String>()

    fun clearNameIdMappings() {
        nameToIdMap.clear()
    }

    fun addNameIdMapping(name: String, id: String) {
        nameToIdMap[name] = id
    }

    fun toEntity(dto: PatternDTO, key: String? = null): Pattern {
        // Use the provided key or name as ID
        val id = key ?: dto.name
        android.util.Log.d("VideoTimeDebug", "Converting - Name: ${dto.name}, ID: $id, Start: ${dto.videoStartTime}, End: ${dto.videoEndTime}")
        return Pattern(
            id = id,
            name = dto.name,
            difficulty = dto.difficulty,
            siteswap = dto.siteswap,
            num = dto.numberOfObjects?.toString(),
            explanation = dto.explanation,
            gifUrl = dto.gifUrl,
            video = dto.video,
            videoStartTime = dto.videoStartTime,
            videoEndTime = dto.videoEndTime,
            url = dto.url,
            tags = dto.tags ?: emptyList(),
            prerequisites = (dto.prerequisites ?: emptyList()).mapNotNull { name ->
                nameToIdMap[name]
            },
            dependents = (dto.dependents ?: emptyList()).mapNotNull { name ->
                nameToIdMap[name]
            },
            related = (dto.related ?: emptyList()).mapNotNull { name ->
                nameToIdMap[name]
            },
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
                videoStartTime = pattern.videoStartTime,
                videoEndTime = pattern.videoEndTime,
                url = pattern.url,
                tags = pattern.tags,
                prerequisites = pattern.prerequisites,
                dependents = pattern.dependents,
                related = pattern.related,
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
