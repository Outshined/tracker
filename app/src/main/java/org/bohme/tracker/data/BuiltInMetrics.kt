package org.bohme.tracker.data

object BuiltInMetrics {
    const val WEIGHT_GRAPH_MIN = 100.0
    const val WEIGHT_GRAPH_MAX = 300.0
    const val BHB_GRAPH_MIN = 0.0
    const val BHB_GRAPH_MAX = 5.0
    const val GLUCOSE_GRAPH_MIN = 50.0
    const val GLUCOSE_GRAPH_MAX = 250.0
    const val BLOOD_PRESSURE_GRAPH_MIN = 40.0
    const val BLOOD_PRESSURE_GRAPH_MAX = 200.0

    fun defaultGraphRange(id: String): Pair<Double, Double>? = when (id) {
        "weight" -> WEIGHT_GRAPH_MIN to WEIGHT_GRAPH_MAX
        "bhb" -> BHB_GRAPH_MIN to BHB_GRAPH_MAX
        "glucose" -> GLUCOSE_GRAPH_MIN to GLUCOSE_GRAPH_MAX
        "blood_pressure" -> BLOOD_PRESSURE_GRAPH_MIN to BLOOD_PRESSURE_GRAPH_MAX
        else -> null
    }

    val ALL: List<MetricDef> = listOf(
        MetricDef(
            id = "weight",
            label = "Weight",
            fields = listOf(FieldDef(id = "lb", label = "Weight", unit = "lb")),
            graphMin = WEIGHT_GRAPH_MIN,
            graphMax = WEIGHT_GRAPH_MAX,
        ),
        MetricDef(
            id = "bhb",
            label = "BHB",
            fields = listOf(FieldDef(id = "mmol_l", label = "BHB", unit = "mmol/L")),
            graphMin = BHB_GRAPH_MIN,
            graphMax = BHB_GRAPH_MAX,
        ),
        MetricDef(
            id = "glucose",
            label = "Blood glucose",
            fields = listOf(FieldDef(id = "mg_dl", label = "Glucose", unit = "mg/dL")),
            graphMin = GLUCOSE_GRAPH_MIN,
            graphMax = GLUCOSE_GRAPH_MAX,
        ),
        MetricDef(
            id = "blood_pressure",
            label = "Blood pressure",
            fields = listOf(
                FieldDef(id = "systolic", label = "Systolic", unit = "mmHg"),
                FieldDef(id = "diastolic", label = "Diastolic", unit = "mmHg"),
                FieldDef(id = "pulse", label = "Pulse", unit = "bpm"),
            ),
            graphMin = BLOOD_PRESSURE_GRAPH_MIN,
            graphMax = BLOOD_PRESSURE_GRAPH_MAX,
        ),
    )
}
