package com.example.geotracker.data

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import org.json.JSONArray
import org.json.JSONObject

object VisitRepository {
    private const val TAG = "VisitRepository"
    private const val PREFS_NAME = "visit_repository"
    private const val EVENTS_KEY = "visit_events"
    private const val OBJECTS_KEY = "visit_objects"

    private val visitEvents = mutableListOf<VisitEvent>()
    private var visitObjects: List<VisitObject> = MockVisitObjects.create()
    private var prefs: SharedPreferences? = null

    fun initialize(context: Context) {
        if (prefs != null) return

        prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        visitObjects = loadVisitObjects().map { visitObject ->
            val savedStatus = prefs?.getString(statusKey(visitObject.objectId), null)
                ?.let { savedValue -> runCatching { VisitStatus.valueOf(savedValue) }.getOrNull() }
                ?: visitObject.status
            visitObject.copy(status = savedStatus)
        }

        loadEvents()
    }

    fun getObjects(): List<VisitObject> = visitObjects

    fun getEvents(): List<VisitEvent> = visitEvents.toList()

    fun getPendingEvents(): List<VisitEvent> {
        return visitEvents.filter { it.syncStatus != SyncStatus.SYNCED }
    }

    fun getPendingEventCount(): Int {
        return getPendingEvents().size
    }

    fun markEventsSynced(eventIds: Set<Long>) {
        if (eventIds.isEmpty()) return

        val now = System.currentTimeMillis()
        replaceEventsById(eventIds) { visitEvent ->
            visitEvent.copy(
                syncStatus = SyncStatus.SYNCED,
                lastSyncAttemptMillis = now
            )
        }
        Log.d(TAG, "Events marked as synced: count=${eventIds.size}")
    }

    fun markEventsSyncFailed(eventIds: Set<Long>) {
        if (eventIds.isEmpty()) return

        val now = System.currentTimeMillis()
        replaceEventsById(eventIds) { visitEvent ->
            visitEvent.copy(
                syncStatus = SyncStatus.FAILED,
                lastSyncAttemptMillis = now
            )
        }
        Log.d(TAG, "Events marked as failed: count=${eventIds.size}")
    }

    fun addObject(
        address: String,
        latitude: Double,
        longitude: Double,
        radiusMeters: Float,
        dwellMinutes: Int
    ): VisitObject {
        val nextId = (visitObjects.maxOfOrNull { it.objectId } ?: 0) + 1
        val visitObject = VisitObject(
            objectId = nextId,
            address = address,
            latitude = latitude,
            longitude = longitude,
            radiusMeters = radiusMeters,
            dwellMinutes = dwellMinutes
        )

        visitObjects = visitObjects + visitObject
        persistVisitObjects()
        Log.d(TAG, "Object added: objectId=${visitObject.objectId}")
        return visitObject
    }

    fun removeObject(objectId: Int): Boolean {
        val exists = visitObjects.any { it.objectId == objectId }
        if (!exists) return false

        visitObjects = visitObjects.filterNot { it.objectId == objectId }
        visitEvents.removeAll { it.objectId == objectId }

        prefs?.edit()
            ?.remove(statusKey(objectId))
            ?.apply()

        persistVisitObjects()
        persistEvents()
        Log.d(TAG, "Object removed: objectId=$objectId")
        return true
    }

    fun updateStatusFromTransition(objectId: Int, transitionName: String): VisitObject? {
        val currentObject = visitObjects.firstOrNull { it.objectId == objectId } ?: run {
            Log.d(TAG, "Object not found for event: objectId=$objectId transition=$transitionName")
            return null
        }

        val newStatus = when (transitionName) {
            "ENTER" -> VisitStatus.IN_ZONE
            "DWELL" -> VisitStatus.VISITED
            "EXIT" -> {
                if (currentObject.status == VisitStatus.VISITED) {
                    VisitStatus.LEFT_AFTER_VISIT
                } else {
                    VisitStatus.NOT_VISITED
                }
            }
            else -> currentObject.status
        }

        visitObjects = visitObjects.map { visitObject ->
            if (visitObject.objectId == objectId) {
                visitObject.copy(status = newStatus)
            } else {
                visitObject
            }
        }

        visitEvents.add(
            VisitEvent(
                eventId = System.currentTimeMillis() + visitEvents.size,
                objectId = objectId,
                transitionName = transitionName,
                statusAfterEvent = newStatus
            )
        )

        persistStatus(objectId, newStatus)
        persistVisitObjects()
        persistEvents()

        Log.d(TAG, "Status set: objectId=$objectId transition=$transitionName status=$newStatus")
        return visitObjects.first { it.objectId == objectId }
    }

    private fun loadVisitObjects(): List<VisitObject> {
        val savedObjects = prefs?.getString(OBJECTS_KEY, null)
        if (savedObjects.isNullOrBlank()) {
            val defaultObjects = MockVisitObjects.create()
            visitObjects = defaultObjects
            persistVisitObjects()
            return defaultObjects
        }

        return runCatching {
            val jsonArray = JSONArray(savedObjects)
            List(jsonArray.length()) { index ->
                val jsonObject = jsonArray.getJSONObject(index)
                VisitObject(
                    objectId = jsonObject.getInt("objectId"),
                    address = jsonObject.getString("address"),
                    latitude = jsonObject.getDouble("latitude"),
                    longitude = jsonObject.getDouble("longitude"),
                    radiusMeters = jsonObject.getDouble("radiusMeters").toFloat(),
                    dwellMinutes = jsonObject.getInt("dwellMinutes"),
                    status = runCatching {
                        VisitStatus.valueOf(jsonObject.optString("status"))
                    }.getOrDefault(VisitStatus.NOT_VISITED)
                )
            }
        }.getOrElse { exception ->
            Log.d(TAG, "Failed to load saved objects, using mock list", exception)
            MockVisitObjects.create()
        }
    }

    private fun persistVisitObjects() {
        val jsonArray = JSONArray()
        visitObjects.forEach { visitObject ->
            jsonArray.put(
                JSONObject()
                    .put("objectId", visitObject.objectId)
                    .put("address", visitObject.address)
                    .put("latitude", visitObject.latitude)
                    .put("longitude", visitObject.longitude)
                    .put("radiusMeters", visitObject.radiusMeters.toDouble())
                    .put("dwellMinutes", visitObject.dwellMinutes)
                    .put("status", visitObject.status.name)
            )
        }

        prefs?.edit()
            ?.putString(OBJECTS_KEY, jsonArray.toString())
            ?.apply()
    }

    private fun persistStatus(objectId: Int, status: VisitStatus) {
        prefs?.edit()
            ?.putString(statusKey(objectId), status.name)
            ?.apply()
    }

    private fun persistEvents() {
        val jsonArray = JSONArray()
        visitEvents.forEach { visitEvent ->
            jsonArray.put(
                JSONObject()
                    .put("eventId", visitEvent.eventId)
                    .put("timestampMillis", visitEvent.timestampMillis)
                    .put("objectId", visitEvent.objectId)
                    .put("transitionName", visitEvent.transitionName)
                    .put("statusAfterEvent", visitEvent.statusAfterEvent.name)
                    .put("syncStatus", visitEvent.syncStatus.name)
                    .put("lastSyncAttemptMillis", visitEvent.lastSyncAttemptMillis)
            )
        }

        prefs?.edit()
            ?.putString(EVENTS_KEY, jsonArray.toString())
            ?.apply()
    }

    private fun parseEvent(eventLine: String): VisitEvent? {
        val parts = eventLine.split("|")
        if (parts.size != 4) return null

        val timestampMillis = parts[0].toLongOrNull() ?: return null
        val status = runCatching { VisitStatus.valueOf(parts[3]) }.getOrNull() ?: return null

        return VisitEvent(
            eventId = timestampMillis,
            timestampMillis = timestampMillis,
            objectId = parts[1].toIntOrNull() ?: return null,
            transitionName = parts[2],
            statusAfterEvent = status
        )
    }

    private fun loadEvents() {
        visitEvents.clear()

        when (val savedEvents = prefs?.all?.get(EVENTS_KEY)) {
            is String -> {
                if (savedEvents.isBlank()) return

                runCatching {
                    val jsonArray = JSONArray(savedEvents)
                    for (index in 0 until jsonArray.length()) {
                        parseEvent(jsonArray.getJSONObject(index))?.let { visitEvents.add(it) }
                    }
                }.onFailure { exception ->
                    Log.d(TAG, "Failed to load saved events json", exception)
                }
            }
            is Set<*> -> {
                savedEvents
                    .filterIsInstance<String>()
                    .sorted()
                    .forEach { eventLine ->
                        parseEvent(eventLine)?.let { visitEvents.add(it) }
                    }
            }
        }
    }

    private fun parseEvent(jsonObject: JSONObject): VisitEvent? {
        val status = runCatching {
            VisitStatus.valueOf(jsonObject.getString("statusAfterEvent"))
        }.getOrNull() ?: return null

        val syncStatus = runCatching {
            SyncStatus.valueOf(jsonObject.optString("syncStatus", SyncStatus.PENDING.name))
        }.getOrDefault(SyncStatus.PENDING)

        return VisitEvent(
            eventId = jsonObject.optLong("eventId", jsonObject.getLong("timestampMillis")),
            timestampMillis = jsonObject.getLong("timestampMillis"),
            objectId = jsonObject.getInt("objectId"),
            transitionName = jsonObject.getString("transitionName"),
            statusAfterEvent = status,
            syncStatus = syncStatus,
            lastSyncAttemptMillis = if (jsonObject.isNull("lastSyncAttemptMillis")) {
                null
            } else {
                jsonObject.getLong("lastSyncAttemptMillis")
            }
        )
    }

    private fun replaceEventsById(
        eventIds: Set<Long>,
        replacement: (VisitEvent) -> VisitEvent
    ) {
        for (index in visitEvents.indices) {
            val visitEvent = visitEvents[index]
            if (visitEvent.eventId in eventIds) {
                visitEvents[index] = replacement(visitEvent)
            }
        }
        persistEvents()
    }

    private fun statusKey(objectId: Int): String = "status_$objectId"
}
