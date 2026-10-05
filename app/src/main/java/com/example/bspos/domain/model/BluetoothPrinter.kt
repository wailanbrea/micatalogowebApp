package com.example.bspos.domain.model

enum class ReceiptPaperWidth(val label: String, val columns: Int) {
    MM58("58 mm", 32),
    MM80("80 mm", 48)
}

data class BluetoothPrinter(
    val name: String,
    val address: String,
    val isDefault: Boolean = false,
    val paperWidth: ReceiptPaperWidth = ReceiptPaperWidth.MM58
)
