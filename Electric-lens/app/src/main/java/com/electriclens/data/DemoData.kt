package com.electriclens.data

/**
 * Static demo content used to drive the mock LOTO flow.
 * No network, no persistence — everything is hard-coded for the hackathon demo.
 */
data class Manual(
    val fileName: String,
    val faultCode: String,
    val meaning: String,
    val likelyCause: String,
    val faultType: String
)

data class Asset(
    val name: String,
    val vfdId: String,
    val location: String,
    val isolationPoints: List<String>,
    val lockPoint: String
)

object DemoData {
    val manual = Manual(
        "PowerFlex_753_VFD_Manual.pdf",
        "F071 OC1",
        "Overcurrent Phase B",
        "Possible shorted motor winding or load-side fault",
        "Overcurrent"
    )

    val asset = Asset(
        "Conveyor CV-104 VFD",
        "VFD-104",
        "MCC-2 | Bucket 17",
        listOf("B-201", "B-205"),
        "MCC-2 Bucket 17"
    )
}
