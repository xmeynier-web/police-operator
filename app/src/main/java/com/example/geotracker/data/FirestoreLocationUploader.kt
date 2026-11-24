package com.example.geotracker.data

import android.util.Log
import com.example.geotracker.location.GpsSample
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.tasks.await

class FirestoreLocationUploader(
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance(),
    private val liveCollection: String = "live_positions",
    private val historySubcollection: String = "history",
    private val recordHistory: Boolean = true
) {

    suspend fun uploadSample(
        unitId: String,
        sample: GpsSample,
        isTracking: Boolean = true
    ): Result<Unit> {
        return try {
            val speedKmh = if (sample.speedMetersPerSec > 0.15f) {
                sample.speedMetersPerSec * 3.6f
            } else {
                0f
            }

            val payload = hashMapOf<String, Any?>(
                "unitId" to unitId,
                "latitude" to sample.latitude,
                "longitude" to sample.longitude,
                "accuracyMeters" to sample.accuracyMeters,
                "speedMetersPerSec" to sample.speedMetersPerSec,
                "speedKmh" to speedKmh,
                "bearingDegrees" to sample.bearingDegrees,
                "altitudeMeters" to sample.altitudeMeters,
                "isTracking" to isTracking,
                "updatedAt" to FieldValue.serverTimestamp()
            )

            val liveDocRef = firestore.collection(liveCollection).document(unitId)
            liveDocRef.set(payload, SetOptions.merge()).await()

            if (recordHistory) {
                liveDocRef.collection(historySubcollection).add(payload).await()
            }

            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Échec de synchronisation Firestore pour $unitId", e)
            Result.failure(e)
        }
    }

    suspend fun markTrackingStopped(unitId: String): Result<Unit> {
        return try {
            val updates = mapOf(
                "isTracking" to false,
                "speedMetersPerSec" to 0f,
                "speedKmh" to 0f,
                "updatedAt" to FieldValue.serverTimestamp()
            )
            firestore.collection(liveCollection)
                .document(unitId)
                .set(updates, SetOptions.merge())
                .await()
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Échec mise à jour arrêt pour $unitId", e)
            Result.failure(e)
        }
    }

    companion object {
        private const val TAG = "FirestoreLocationUploader"
    }
}
