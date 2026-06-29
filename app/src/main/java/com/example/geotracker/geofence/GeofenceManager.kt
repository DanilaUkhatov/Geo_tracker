package com.example.geotracker.geofence

import android.Manifest
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import com.example.geotracker.data.VisitObject
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.Geofence
import com.google.android.gms.location.GeofencingClient
import com.google.android.gms.location.GeofencingRequest
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource

class GeofenceManager(private val context: Context) {
    private val geofencingClient: GeofencingClient =
        LocationServices.getGeofencingClient(context)
    private val fusedLocationClient: FusedLocationProviderClient =
        LocationServices.getFusedLocationProviderClient(context)

    private val geofencePendingIntent: PendingIntent by lazy {
        val intent = Intent(context, GeofenceBroadcastReceiver::class.java)
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }
        PendingIntent.getBroadcast(context, GEOFENCE_REQUEST_CODE, intent, flags)
    }

    fun hasRequiredPermissions(): Boolean {
        val fineLocationGranted = context.checkSelfPermission(
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        val backgroundLocationGranted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            context.checkSelfPermission(
                Manifest.permission.ACCESS_BACKGROUND_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }

        return fineLocationGranted && backgroundLocationGranted
    }

    fun registerGeofences(
        visitObjects: List<VisitObject>,
        onSuccess: () -> Unit,
        onError: (Exception) -> Unit
    ) {
        if (!hasRequiredPermissions()) {
            onError(SecurityException("Location permissions are not granted"))
            return
        }

        logCurrentLocation()

        val geofences = visitObjects.map { visitObject ->
            Geofence.Builder()
                .setRequestId(visitObject.objectId.toString())
                .setCircularRegion(
                    visitObject.latitude,
                    visitObject.longitude,
                    visitObject.radiusMeters
                )
                .setTransitionTypes(
                    Geofence.GEOFENCE_TRANSITION_ENTER or
                        Geofence.GEOFENCE_TRANSITION_DWELL or
                        Geofence.GEOFENCE_TRANSITION_EXIT
                )
                .setLoiteringDelay(visitObject.dwellMinutes * 60 * 1000)
                .setExpirationDuration(Geofence.NEVER_EXPIRE)
                .build()
        }

        val request = GeofencingRequest.Builder()
            .setInitialTrigger(
                GeofencingRequest.INITIAL_TRIGGER_ENTER or
                    GeofencingRequest.INITIAL_TRIGGER_DWELL
            )
            .addGeofences(geofences)
            .build()

        geofencingClient.removeGeofences(geofencePendingIntent)
            .addOnCompleteListener {
                addGeofences(request, geofences.size, visitObjects, onSuccess, onError)
            }
    }

    private fun logCurrentLocation() {
        if (!hasRequiredPermissions()) return

        try {
            fusedLocationClient.lastLocation
                .addOnSuccessListener { location ->
                    Log.d(
                        TAG,
                        "Last location before geofence registration: " +
                            "lat=${location?.latitude}, lon=${location?.longitude}, " +
                            "accuracy=${location?.accuracy}"
                    )
                }
                .addOnFailureListener { exception ->
                    Log.d(TAG, "Last location failed: ${exception.message}", exception)
                }

            fusedLocationClient.getCurrentLocation(
                Priority.PRIORITY_HIGH_ACCURACY,
                CancellationTokenSource().token
            ).addOnSuccessListener { location ->
                Log.d(
                    TAG,
                    "Current location before geofence registration: " +
                        "lat=${location?.latitude}, lon=${location?.longitude}, " +
                        "accuracy=${location?.accuracy}"
                )
            }.addOnFailureListener { exception ->
                Log.d(TAG, "Current location failed: ${exception.message}", exception)
            }
        } catch (exception: SecurityException) {
            Log.d(TAG, "Current location skipped: permissions revoked", exception)
        }
    }

    private fun addGeofences(
        request: GeofencingRequest,
        geofenceCount: Int,
        visitObjects: List<VisitObject>,
        onSuccess: () -> Unit,
        onError: (Exception) -> Unit
    ) {
        if (!hasRequiredPermissions()) {
            onError(SecurityException("Location permissions are not granted"))
            return
        }

        try {
            geofencingClient.addGeofences(request, geofencePendingIntent)
                .addOnSuccessListener {
                    Log.d(TAG, "Geofences registered: count=$geofenceCount")
                    visitObjects.forEach { visitObject ->
                        Log.d(
                            TAG,
                            "Registered geofence: objectId=${visitObject.objectId}, " +
                                "radius=${visitObject.radiusMeters}, dwell=${visitObject.dwellMinutes} min"
                        )
                    }
                    onSuccess()
                }
                .addOnFailureListener { exception ->
                    val apiException = exception as? ApiException
                    val statusCode = apiException?.statusCode
                    Log.d(
                        TAG,
                        "Geofence registration failed: statusCode=$statusCode message=${exception.message}",
                        exception
                    )
                    onError(exception)
                }
        } catch (exception: SecurityException) {
            Log.d(TAG, "Geofence registration failed: permissions revoked", exception)
            onError(exception)
        }
    }

    companion object {
        private const val TAG = "GeofenceManager"
        private const val GEOFENCE_REQUEST_CODE = 1001
    }
}
