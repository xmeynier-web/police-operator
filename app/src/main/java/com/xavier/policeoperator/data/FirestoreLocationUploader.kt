package com.xavier.policeoperator.data

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.SetOptions
import com.xavier.policeoperator.model.CsuDispatchMessage
import com.xavier.policeoperator.model.DEFAULT_EQUIPMENT_LIST
import com.xavier.policeoperator.model.GpsSample
import com.xavier.policeoperator.model.PatrolStatus

class FirestoreLocationUploader(
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance(),
    private val auth: FirebaseAuth = FirebaseAuth.getInstance()
) {
    private var isSigningIn = false
    private val pendingActions = mutableListOf<() -> Unit>()

    private fun ensureSignedIn(
        onError: (Exception) -> Unit = {},
        onReady: () -> Unit
    ) {
        val currentUser = auth.currentUser
        if (currentUser != null) {
            onReady()
            return
        }

        synchronized(this) {
            pendingActions.add(onReady)
            if (isSigningIn) {
                return
            }
            isSigningIn = true
        }

        auth.signInAnonymously()
            .addOnSuccessListener {
                val actionsToRun: List<() -> Unit>
                synchronized(this) {
                    isSigningIn = false
                    actionsToRun = pendingActions.toList()
                    pendingActions.clear()
                }
                actionsToRun.forEach { action ->
                    runCatching { action() }
                }
            }
            .addOnFailureListener { exception ->
                synchronized(this) {
                    isSigningIn = false
                    pendingActions.clear()
                }
                onError(exception)
            }
    }

    fun upsertUnitState(
        unitId: String,
        crewCount: Int,
        status: PatrolStatus,
        isTracking: Boolean,
        sample: GpsSample?,
        equipment: List<String> = DEFAULT_EQUIPMENT_LIST,
        recordHistory: Boolean = false,
        onError: (Exception) -> Unit = {}
    ) {
        ensureSignedIn(onError = onError) {
            val cleanUnitId = unitId.trim().ifEmpty { "ALPHA 1" }
            val cleanCrewCount = crewCount.coerceAtLeast(1)
            val docRef = firestore.collection("live_positions").document(cleanUnitId)

            val payload = hashMapOf<String, Any>(
                "unitId" to cleanUnitId,
                "crewCount" to cleanCrewCount,
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
                .addOnFailureListener(onError)

            if (recordHistory && sample != null && isTracking) {
                docRef.collection("history").add(
                    hashMapOf(
                        "latitude" to sample.latitude,
                        "longitude" to sample.longitude,
                        "speedKmh" to sample.speedKmh,
                        "crewCount" to cleanCrewCount,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).addOnFailureListener(onError)
            }
        }
    }

    fun observeCsuMessages(
        unitId: String,
        onUpdate: (List<CsuDispatchMessage>) -> Unit,
        onError: (Exception) -> Unit = {}
    ): ListenerRegistration {
        var innerRegistration: ListenerRegistration? = null
        var cancelled = false

        ensureSignedIn(onError = onError) {
            if (cancelled) return@ensureSignedIn
            val targetUnit = unitId.trim().lowercase()
            innerRegistration = firestore.collection("missions")
                .orderBy("createdAt", Query.Direction.DESCENDING)
                .limit(25)
                .addSnapshotListener { snapshot, error ->
                    if (error != null) {
                        onError(error)
                        return@addSnapshotListener
                    }
                    val items = snapshot?.documents.orEmpty().mapNotNull { doc ->
                        if (doc.getBoolean("archived") == true) {
                            return@mapNotNull null
                        }
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

        return ListenerRegistration {
            cancelled = true
            innerRegistration?.remove()
        }
    }

    fun acknowledgeMessage(
        unitId: String,
        message: CsuDispatchMessage,
        onError: (Exception) -> Unit = {}
    ) {
        ensureSignedIn(onError = onError) {
            val cleanUnitId = unitId.trim().ifEmpty { "ALPHA 1" }

            firestore.collection("missions").document(message.id).set(
                hashMapOf(
                    "acknowledgedBy" to cleanUnitId,
                    "acknowledgedAt" to FieldValue.serverTimestamp()
                ),
                SetOptions.merge()
            ).addOnFailureListener(onError)

            firestore.collection("mci_logs").add(
                hashMapOf(
                    "unitId" to cleanUnitId,
                    "type" to "ACK",
                    "message" to "Validé par $cleanUnitId : ${message.title}",
                    "createdAt" to FieldValue.serverTimestamp()
                )
            ).addOnFailureListener(onError)
        }
    }
}
