package com.store478.rtvcheck.data

data class ItemResult(
    val upc: String,
    val sku: String,
    val description: String,
    val status: String,
    val supplierNo: String,
    val supplierName: String,
    val keterangan: String
) {
    val isRtv: Boolean
        get() = keterangan.uppercase().contains("RTV") && !keterangan.uppercase().contains("NON RTV")

    val isNonRtv: Boolean
        get() = keterangan.uppercase().contains("NON RTV")

    val isTukarGuling: Boolean
        get() = keterangan.uppercase().contains("TUKAR GULING")
}

sealed class LookupState {
    object Idle : LookupState()
    object Loading : LookupState()
    data class Found(val item: ItemResult) : LookupState()
    data class NotFound(val query: String) : LookupState()
}
