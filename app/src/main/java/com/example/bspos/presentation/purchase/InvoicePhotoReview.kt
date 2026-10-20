package com.example.bspos.presentation.purchase

import com.example.bspos.data.micatalogo.dto.PurchaseProductDto

data class InvoicePhotoReview(val text: String, val lines: List<PurchaseLineDraft>)

/** Product suggestions only. Never guesses quantities, prices, taxes or currencies. */
internal fun photoInvoiceSuggestions(text: String, products: List<PurchaseProductDto>): List<PurchaseLineDraft> {
    fun mentioned(value: String): Boolean = value.length >= 3 &&
        Regex("(?<![\\p{L}\\p{N}])${Regex.escape(value)}(?![\\p{L}\\p{N}])", RegexOption.IGNORE_CASE).containsMatchIn(text)
    return products.filter { mentioned(it.code.trim()) || mentioned(it.name.trim()) }
        .distinctBy { it.id }.take(100)
        .map { PurchaseLineDraft(productId = it.id, quantity = "", unitCost = "") }
        .ifEmpty { listOf(PurchaseLineDraft(quantity = "")) }
}
