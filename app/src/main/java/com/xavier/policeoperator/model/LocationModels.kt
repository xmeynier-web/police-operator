package com.xavier.policeoperator.model

enum class PatrolStatus(
    val code: String,
    val label: String,
    val colorHex: Long
) {
    PATROUILLE("PATROUILLE", "En patrouille", 0xFF2563EB),
    INTERVENTION("INTERVENTION", "En intervention", 0xFFDC2626),
    SUR_LIEUX("SUR_LIEUX", "Sur les lieux", 0xFF8B5CF6),
    CONTROLE_ROUTE("CONTROLE_ROUTE", "Contrôle route", 0xFF06B6D4),
    DISPONIBLE("DISPONIBLE", "Disponible", 0xFF16A34A),
    PAUSE("PAUSE", "En pause", 0xFFD97706),
    FIN_DE_SERVICE("FIN_DE_SERVICE", "Fin de service", 0xFF475569);

    companion object {
        fun fromCode(code: String?): PatrolStatus {
            val normalized = code?.trim()?.uppercase()
            return entries.firstOrNull { it.code == normalized } ?: PATROUILLE
        }
    }
}

val DEFAULT_EQUIPMENT_LIST = listOf(
    "Caméra piéton",
    "Terminal / Radio",
    "Éthylotest / Dépistage",
    "Trousse secours / DSA",
    "Cônes / Signalisation"
)

data class GpsSample(
    val latitude: Double,
    val longitude: Double,
    val speedKmh: Double = 0.0,
    val accuracyMeters: Float = 0f,
    val timestampMillis: Long = System.currentTimeMillis()
)

data class CsuDispatchMessage(
    val id: String,
    val unitId: String,
    val title: String,
    val notes: String,
    val acknowledgedBy: String? = null
)
