package com.example.geotracker.data

data class VisitEvent(
    val eventId: Long = System.currentTimeMillis(),
    val objectId: Int,
    val transitionName: String,
    val statusAfterEvent: VisitStatus,
    val timestampMillis: Long = System.currentTimeMillis(),
    val syncStatus: SyncStatus = SyncStatus.PENDING,
    val lastSyncAttemptMillis: Long? = null
)
