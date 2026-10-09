package com.example.bspos.presentation.orders

import com.example.bspos.core.money.MoneyUtils

internal fun calculateMixedCredit(totalCents: Long, downPayment: String): Long? {
    if (totalCents <= 0L) return null
    val text = downPayment.trim()
    val normalized = if (text.matches(Regex("[0-9]{1,3}(,[0-9]{3})+(\\.[0-9]{1,2})?"))) {
        text.replace(",", "")
    } else {
        text
    }
    val downPaymentCents = MoneyUtils.parseDecimalToCents(normalized) ?: return null
    return if (downPaymentCents in 1 until totalCents) totalCents - downPaymentCents else null
}
