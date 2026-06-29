package com.example.geotracker.data

object MockVisitObjects {
    fun create(): List<VisitObject> = listOf(
        VisitObject(
            objectId = 1,
            address = "Москва, Красная площадь, 1",
            latitude = 55.753930,
            longitude = 37.620795,
            radiusMeters = 180f,
            dwellMinutes = 1
        ),
        VisitObject(
            objectId = 2,
            address = "Москва, ул. Тверская, 7",
            latitude = 55.761590,
            longitude = 37.609460,
            radiusMeters = 170f,
            dwellMinutes = 1
        ),
        VisitObject(
            objectId = 3,
            address = "Москва, Никольская ул., 10",
            latitude = 55.757071,
            longitude = 37.623008,
            radiusMeters = 150f,
            dwellMinutes = 1
        )
    )
}
