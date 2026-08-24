package org.paul.tracker.data

object BuiltInMetrics {
    val ALL: List<MetricDef> = listOf(
        MetricDef(
            id = "weight",
            label = "Weight",
            fields = listOf(FieldDef(id = "lb", label = "Weight", unit = "lb")),
        ),
        MetricDef(
            id = "bhb",
            label = "BHB",
            fields = listOf(FieldDef(id = "mmol_l", label = "BHB", unit = "mmol/L")),
        ),
        MetricDef(
            id = "glucose",
            label = "Blood glucose",
            fields = listOf(FieldDef(id = "mg_dl", label = "Glucose", unit = "mg/dL")),
        ),
        MetricDef(
            id = "blood_pressure",
            label = "Blood pressure",
            fields = listOf(
                FieldDef(id = "systolic", label = "Systolic", unit = "mmHg"),
                FieldDef(id = "diastolic", label = "Diastolic", unit = "mmHg"),
                FieldDef(id = "pulse", label = "Pulse", unit = "bpm"),
            ),
        ),
    )
}
