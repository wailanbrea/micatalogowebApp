package com.example.bspos.presentation.pos

import com.example.bspos.domain.model.Product

/** Shared POS labels so compact and grid terminals describe inventory the same way. */
internal fun Product.isDecantProduct(): Boolean = remoteSaleUnit.equals("decant", ignoreCase = true)

internal fun Product.decantPresentationLabel(): String? {
    if (!isDecantProduct()) return null
    return remoteVolumeMl?.takeIf { it > 0 }?.let { "DECANT · ${it} ml" } ?: "DECANT"
}

internal fun Product.posStockLabel(available: Long, allowNegativeStock: Boolean): String = when {
    remoteSaleUnit.equals("service", ignoreCase = true) -> "Servicio · sin inventario"
    available > 0 -> "En stock ($available)"
    allowNegativeStock -> "Stock negativo permitido"
    else -> "Agotado"
}

internal fun Product.sourceProductName(sourceNames: Map<String, String>): String? =
    remoteSourceProductId?.let(sourceNames::get)
