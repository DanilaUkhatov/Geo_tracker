package com.example.geotracker.data

data class VisitObject(
    val objectId: Int,
    val address: String,
    val latitude: Double,
    val longitude: Double,
    val radiusMeters: Float,
    val dwellMinutes: Int,
    val status: VisitStatus = VisitStatus.NOT_VISITED
)
