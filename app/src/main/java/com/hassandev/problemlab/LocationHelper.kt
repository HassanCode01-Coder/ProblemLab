package com.hassandev.problemlab

import android.annotation.SuppressLint
import android.content.Context
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

data class GpsPosition(
    val latitude: Double,
    val longitude: Double
)

@SuppressLint("MissingPermission")
suspend fun getCurrentLocation(context: Context): GpsPosition? {
    val client = LocationServices.getFusedLocationProviderClient(context)

    return suspendCancellableCoroutine { continuation ->
        client.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, null)
            .addOnSuccessListener { location ->
                if (location != null) {
                    continuation.resume(
                        GpsPosition(location.latitude, location.longitude)
                    )
                } else {
                    continuation.resume(null)
                }
            }
            .addOnFailureListener {
                continuation.resume(null)
            }
    }
}