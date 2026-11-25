package com.example.geotracker.xavier.policeoperator.data

import com.example.geotracker.xavier.policeoperator.model.CsuDispatchMessage
import com.example.geotracker.xavier.policeoperator.model.GpsSample
import com.example.geotracker.xavier.policeoperator.model.PatrolStatus
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.SetOptions

class FirestoreLocationUploader(
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance()
) {
    fun upsertUnitState(
        unitId: String,
        status: PatrolStatus,
        isTracking: Boolean,
        sample: GpsSample?,
        equipment: List<String> = listOf("Caméra piéton", "Terminal / Radio"),
        recordHistory: Boolean = false
    ) {
        val cleanUnitId = unitId.trim().ifEmpty { "ALPHA 1" }
        val docRef = firestore.collection("live_positions").document(cleanUnitId)

        val payload = hashMapOf<String, Any>(
            "unitId" to cleanUnitId,
            "operationalStatus" to status.code,
            "operationalStatusLabel" to status.label,
            "isTracking" to isTracking,
            "speedKmh" to (sample?.speedKmh ?: 0.0),
            "equipment" to equipment,
            "updatedAt" to FieldValue.serverTimestamp()
        )
        if (sample != null) {
            payload["latitude"] = sample.latitude
            payload["longitude"] = sample.longitude
        }

        docRef.set(payload, SetOptions.merge())

        if (recordHistory && sample != null && isTracking) {
            docRef.collection("history").add(
                hashMapOf(
                    "latitude" to sample.latitude,
                    "longitude" to sample.longitude,
                    "speedKmh" to sample.speedKmh,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            )
        }
    }

    fun observeCsuMessages(
        unitId: String,
        onUpdate: (List<CsuDispatchMessage>) -> Unit,
        onError: (Exception) -> Unit = {}
    ): ListenerRegistration {
        val targetUnit = unitId.trim().lowercase()
        return firestore.collection("missions")
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .limit(25)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    onError(error)
                    return@addSnapshotListener
                }
                val items = snapshot?.documents.orEmpty().mapNotNull { doc ->
                    val assignedUnit = (doc.getString("unitId") ?: "").trim()
                    if (assignedUnit.isNotEmpty() && assignedUnit.lowercase() != targetUnit) {
                        return@mapNotNull null
                    }
                    CsuDispatchMessage(
                        id = doc.id,
                        unitId = assignedUnit,
                        title = doc.getString("title") ?: "Message CSU",
                        notes = doc.getString("notes") ?: "",
                        acknowledgedBy = doc.getString("acknowledgedBy")
                    )
                }
                onUpdate(items)
            }
    }

    fun acknowledgeMessage(unitId: String, message: CsuDispatchMessage) {
        val cleanUnitId = unitId.trim().ifEmpty { "ALPHA 1" }

        firestore.collection("missions").document(message.id).set(
            hashMapOf(
                "acknowledgedBy" to cleanUnitId,
                "acknowledgedAt" to FieldValue.serverTimestamp()
            ),
            SetOptions.merge()
        )

        firestore.collection("mci_logs").add(
            hashMapOf(
                "unitId" to cleanUnitId,
                "type" to "ACK",
                "message" to "Validé par $cleanUnitId : ${message.title}",
                "createdAt" to FieldValue.serverTimestamp()
            )
        )
    }
}
