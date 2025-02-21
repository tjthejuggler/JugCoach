package com.example.jugcoach.data.converter

import androidx.room.TypeConverter

class IntegerConverter {
    @TypeConverter
    fun fromInteger(value: Int?): Int? {
        return value
    }

    @TypeConverter
    fun toInteger(value: Int?): Int? {
        return value
    }
}