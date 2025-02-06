package com.example.jugcoach.data.dto

import com.google.gson.annotations.SerializedName

data class PatternDTO(
    val name: String,
    val difficulty: Int?,
    val siteswap: String?,
    @SerializedName("num")
    val numberOfObjects: Int?,
    val explanation: String?,
    val gifUrl: String?,
    val video: String?,
    val url: String?,
    val tags: List<String>?,
    @SerializedName("prereqs")
    val prerequisites: List<String>?,
    val dependents: List<String>?,
    @SerializedName("pre-existing record")
    val record: RecordDTO?
)

data class RecordDTO(
    val catches: Int?,
    val date: String?
)
