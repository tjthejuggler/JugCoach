package com.example.jugcoach.data.converter

import androidx.room.TypeConverter
import com.example.jugcoach.data.entity.ProposalStatus

class ProposalStatusConverter {
    @TypeConverter
    fun fromProposalStatus(status: ProposalStatus): String {
        return status.name
    }

    @TypeConverter
    fun toProposalStatus(status: String): ProposalStatus {
        return ProposalStatus.valueOf(status)
    }
}
