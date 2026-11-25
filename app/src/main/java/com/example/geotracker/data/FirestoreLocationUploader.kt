package com.example.geotracker.data

import android.util.Log
import com.example.geotracker.location.GpsSample
import com.example.geotracker.model.OperationalStatus
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
        unitName: String,
        operationalStatus: OperationalStatus,
        equipment: List<String>,
        missionNote: String,
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
                "unitName" to unitName,
                "operationalStatus" to operationalStatus.code,
                "operationalStatusLabel" to operationalStatus.label,
                "equipment" to equipment,
                "missionNote" to missionNote,
                "latitude" to sample.latitude,
                "longitude" to sample.longitude,
                "accuracyMeters" to sample.accuracyMeters,
                "speedKmh" to speedKmh,
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
            Log.e(TAG, "Échec sync Firestore pour $unitId", e)
            Result.failure(e)
        }
    }

    suspend fun updateUnitStateOnly(
        unitId: String,
        unitName: String,
        operationalStatus: OperationalStatus,
        equipment: List<String>,
        missionNote: String,
        isTracking: Boolean
    ): Result<Unit> {
        return try {
            val updates = mapOf(
                "unitId" to unitId,
                "unitName" to unitName,
                "operationalStatus" to operationalStatus.code,
                "operationalStatusLabel" to operationalStatus.label,
                "equipment" to equipment,
                "missionNote" to missionNote,
                "isTracking" to isTracking,
                "updatedAt" to FieldValue.serverTimestamp()
            )
            firestore.collection(liveCollection)
                .document(unitId)
                .set(updates, SetOptions.merge())
                .await()
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Échec mise à jour état Firestore pour $unitId", e)
            Result.failure(e)
        }
    }

    suspend fun markTrackingStopped(
        unitId: String,
        unitName: String,
        operationalStatus: OperationalStatus,
        equipment: List<String>,
        missionNote: String
    ): Result<Unit> {
        return try {
            val updates = mapOf(
                "unitId" to unitId,
                "unitName" to unitName,
                "operationalStatus" to operationalStatus.code,
                "operationalStatusLabel" to operationalStatus.label,
                "equipment" to equipment,
                "missionNote" to missionNote,
                "isTracking" to false,
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
