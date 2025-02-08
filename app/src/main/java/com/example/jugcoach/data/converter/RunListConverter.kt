package com.example.jugcoach.data.converter

import androidx.room.TypeConverter
import com.example.jugcoach.data.entity.Run
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

class RunListConverter {
    private val gson = Gson()

    @TypeConverter
    fun fromString(value: String): List<Run> {
        val listType = object : TypeToken<List<Run>>() {}.type
        return gson.fromJson(value, listType)
    }

    @TypeConverter
    fun fromList(list: List<Run>): String {
        return gson.toJson(list)
    }
}
