package com.example.bspos.presentation.dashboard

import com.example.bspos.data.micatalogo.api.MiCatalogoApi
import dagger.Lazy
import javax.inject.Inject

data class FirstSaleStatus(val shopId: String? = null, val hasSale: Boolean? = null)

/** Lifetime milestone, independent of the dashboard period and this device's Room history. */
class FirstSaleStatusReader @Inject constructor(private val api: Lazy<MiCatalogoApi>) {
    suspend fun hasSale(shopId: String): Boolean {
        for (status in listOf("paid", "partial", "credit")) {
            val response = api.get().feature(shopId, "sales", period = "all", status = status)
            check(response.isSuccessful) { "No se pudo comprobar la primera venta (${response.code()})." }
            val module = response.body()?.module ?: error("No se recibió el historial de ventas.")
            // The KPI covers the entire history, including invoices outside the first 100 rows.
            val count = module.kpis.firstOrNull { it.label.startsWith("Ventas", ignoreCase = true) }
                ?.value?.filter(Char::isDigit)?.toLongOrNull()
            if (count != null) {
                if (count > 0) return true
            } else if (module.rows.isNotEmpty()) return true
            else error("El servidor no confirmó el conteo histórico.")
        }
        return false
    }
}
