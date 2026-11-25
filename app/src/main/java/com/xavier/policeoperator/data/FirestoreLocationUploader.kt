package com.xavier.policeoperator.data

import android.util.Log
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.xavier.policeoperator.location.GpsSample
import com.xavier.policeoperator.model.OperationalStatus
import kotlinx.coroutines.tasks.await

class FirestoreLocationUploader(
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance(),
    private val liveCollection: String = "live_positions",
    private val historySubcollection: String = "history",
    private val recordHistory: Boolean = true
) {

    suspend fun uploadSample(
        unitId: String,
        operationalStatus: OperationalStatus,
        equipment: List<String>,
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
                "unitName" to unitId,
                "operationalStatus" to operationalStatus.code,
                "operationalStatusLabel" to operationalStatus.label,
                "equipment" to equipment,
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

    suspend fun pushUnitState(
        unitId: String,
        operationalStatus: OperationalStatus,
        equipment: List<String>,
        isTracking: Boolean,
        latitude: Double? = null,
        longitude: Double? = null
    ): Result<Unit> {
        return try {
            val updates = hashMapOf<String, Any?>(
                "unitId" to unitId,
                "unitName" to unitId,
                "operationalStatus" to operationalStatus.code,
                "operationalStatusLabel" to operationalStatus.label,
                "equipment" to equipment,
                "isTracking" to isTracking,
                "updatedAt" to FieldValue.serverTimestamp()
            )
            if (latitude != null && longitude != null) {
                updates["latitude"] = latitude
                updates["longitude"] = longitude
            }
            if (!isTracking) {
                updates["speedKmh"] = 0f
            }

            firestore.collection(liveCollection)
                .document(unitId)
                .set(updates, SetOptions.merge())
                .await()
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Échec push état Firestore pour $unitId", e)
            Result.failure(e)
        }
    }

    companion object {
        private const val TAG = "FirestoreLocationUploader"
    }
}
