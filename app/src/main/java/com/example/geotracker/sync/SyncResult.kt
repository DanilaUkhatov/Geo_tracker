package com.example.geotracker.sync

data class SyncResult(
    val success: Boolean,
    val syncedCount: Int,
    val message: String
)
