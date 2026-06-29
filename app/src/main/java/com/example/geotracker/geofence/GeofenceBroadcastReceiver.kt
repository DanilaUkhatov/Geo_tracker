package com.example.geotracker.geofence

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.example.geotracker.data.SyncSettingsRepository
import com.example.geotracker.data.VisitRepository
import com.example.geotracker.sync.EventSyncManager
import com.google.android.gms.location.Geofence
import com.google.android.gms.location.GeofenceStatusCodes
import com.google.android.gms.location.GeofencingEvent

class GeofenceBroadcastReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        SyncSettingsRepository.initialize(context)
        VisitRepository.initialize(context)

        val geofencingEvent = GeofencingEvent.fromIntent(intent) ?: run {
            Log.d(TAG, "Geofencing event is null")
            return
        }

        if (geofencingEvent.hasError()) {
            val errorMessage = GeofenceStatusCodes.getStatusCodeString(geofencingEvent.errorCode)
            Log.d(TAG, "Geofencing error: $errorMessage")
            return
        }

        val transitionName = transitionName(geofencingEvent.geofenceTransition)
        Log.d(TAG, "Geofence event received: transition=$transitionName")

        geofencingEvent.triggeringGeofences?.forEach { geofence ->
            val objectId = geofence.requestId.toIntOrNull()
            if (objectId == null) {
                Log.d(TAG, "Invalid geofence requestId=${geofence.requestId}")
                return@forEach
            }
            Log.d(TAG, "Geofence event objectId=$objectId transition=$transitionName")
            VisitRepository.updateStatusFromTransition(objectId, transitionName)
        }

        context.sendBroadcast(
            Intent(ACTION_VISIT_STATUS_CHANGED).setPackage(context.packageName)
        )

        EventSyncManager().syncPendingEvents(
            endpointUrl = SyncSettingsRepository.getSyncUrl(),
            allowMockSync = false
        ) { result ->
            Log.d(
                TAG,
                "Auto sync finished: success=${result.success}, " +
                    "synced=${result.syncedCount}, message=${result.message}"
            )
            context.sendBroadcast(
                Intent(ACTION_VISIT_STATUS_CHANGED).setPackage(context.packageName)
            )
        }
    }

    private fun transitionName(transition: Int): String {
        return when (transition) {
            Geofence.GEOFENCE_TRANSITION_ENTER -> "ENTER"
            Geofence.GEOFENCE_TRANSITION_DWELL -> "DWELL"
            Geofence.GEOFENCE_TRANSITION_EXIT -> "EXIT"
            else -> "UNKNOWN"
        }
    }

    companion object {
        const val ACTION_VISIT_STATUS_CHANGED =
            "com.example.geotracker.ACTION_VISIT_STATUS_CHANGED"
        private const val TAG = "GeofenceReceiver"
    }
}
