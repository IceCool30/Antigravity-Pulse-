package com.example.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "server_profiles")
data class ServerProfileEntity(
    @PrimaryKey val id: String,
    val name: String,
    val endpointUrl: String,
    val connectionMode: String, // HYBRID_BRIDGE, DIRECT_TUNNEL, PAIRING_TOKEN
    val authToken: String = "",
    val keepAlivePinned: Boolean = true,
    val lastLatencyMs: Int = 0,
    val isSelected: Boolean = false,
    val updatedAtEpoch: Long = System.currentTimeMillis()
)
