package com.example.geotracker.sync

import android.util.Log
import com.example.geotracker.data.VisitEvent
import com.example.geotracker.data.VisitRepository
import org.json.JSONArray
import org.json.JSONObject
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

class EventSyncManager {
    fun syncPendingEvents(
        endpointUrl: String,
        allowMockSync: Boolean = true,
        callback: (SyncResult) -> Unit
    ) {
        if (isSyncRunning) {
            callback(SyncResult(success = false, syncedCount = 0, message = "Синхронизация уже выполняется"))
            return
        }

        val events = VisitRepository.getPendingEvents()
        if (events.isEmpty()) {
            callback(SyncResult(success = true, syncedCount = 0, message = "Нет событий для синхронизации"))
            return
        }

        isSyncRunning = true
        Thread {
            val eventIds = events.map { it.eventId }.toSet()
            try {
                if (endpointUrl.isBlank()) {
                    if (!allowMockSync) {
                        callback(
                            SyncResult(
                                success = false,
                                syncedCount = 0,
                                message = "URL синхронизации не задан, события остаются в очереди"
                            )
                        )
                        return@Thread
                    }

                    Thread.sleep(MOCK_SYNC_DELAY_MS)
                    VisitRepository.markEventsSynced(eventIds)
                    callback(
                        SyncResult(
                            success = true,
                            syncedCount = events.size,
                            message = "Mock-синхронизация выполнена"
                        )
                    )
                    return@Thread
                }

                postEvents(endpointUrl, events)
                VisitRepository.markEventsSynced(eventIds)
                callback(
                    SyncResult(
                        success = true,
                        syncedCount = events.size,
                        message = "События отправлены на сервер"
                    )
                )
            } catch (exception: Exception) {
                Log.d(TAG, "Event sync failed: ${exception.message}", exception)
                VisitRepository.markEventsSyncFailed(eventIds)
                callback(
                    SyncResult(
                        success = false,
                        syncedCount = 0,
                        message = exception.message ?: "Ошибка синхронизации"
                    )
                )
            } finally {
                isSyncRunning = false
            }
        }.start()
    }

    private fun postEvents(endpointUrl: String, events: List<VisitEvent>) {
        val connection = (URL(endpointUrl).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = CONNECT_TIMEOUT_MS
            readTimeout = READ_TIMEOUT_MS
            doOutput = true
            setRequestProperty("Content-Type", "application/json; charset=utf-8")
            setRequestProperty("Accept", "application/json")
        }

        try {
            OutputStreamWriter(connection.outputStream, Charsets.UTF_8).use { writer ->
                writer.write(buildPayload(events).toString())
            }

            val responseCode = connection.responseCode
            if (responseCode !in 200..299) {
                throw IllegalStateException("Server response code: $responseCode")
            }
        } finally {
            connection.disconnect()
        }
    }

    private fun buildPayload(events: List<VisitEvent>): JSONObject {
        val jsonEvents = JSONArray()
        events.forEach { event ->
            jsonEvents.put(
                JSONObject()
                    .put("eventId", event.eventId)
                    .put("objectId", event.objectId)
                    .put("transitionName", event.transitionName)
                    .put("statusAfterEvent", event.statusAfterEvent.name)
                    .put("timestampMillis", event.timestampMillis)
            )
        }

        return JSONObject()
            .put("events", jsonEvents)
            .put("sentAtMillis", System.currentTimeMillis())
    }

    companion object {
        private const val TAG = "EventSyncManager"
        private const val CONNECT_TIMEOUT_MS = 10_000
        private const val READ_TIMEOUT_MS = 10_000
        private const val MOCK_SYNC_DELAY_MS = 500L

        @Volatile
        private var isSyncRunning = false
    }
}
