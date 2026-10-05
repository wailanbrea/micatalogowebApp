package com.example.bspos.domain.model

enum class CurrencyUnit(val code: String, val symbol: String, val label: String) {
    DOP("DOP", "RD$", "Pesos dominicanos"),
    USD("USD", "US$", "Dólares estadounidenses")
}

data class InvoiceConfig(
    val businessName: String = "MiCatalogo",
    val taxId: String = "",
    val phone: String = "",
    val address: String = "",
    val footer: String = "Gracias por tu compra"
)

data class AppSettings(
    val allowNegativeStock: Boolean = false,
    val automaticBackupsEnabled: Boolean = false,
    val routesEnabled: Boolean = false,
    val currency: CurrencyUnit = CurrencyUnit.DOP,
    val invoice: InvoiceConfig = InvoiceConfig()
)
