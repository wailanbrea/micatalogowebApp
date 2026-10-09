package com.example.bspos.presentation.pos

import com.example.bspos.core.money.MoneyUtils
import com.example.bspos.domain.usecase.PosPaymentSplitInput
import java.time.LocalDate
import java.math.BigDecimal

internal fun validatePaymentWithAutomaticCredit(
    total: Long, cash: String, card: String, transfer: String, useCredit: Boolean,
    customerSelected: Boolean, creditAvailable: Long?, creditEnabled: Boolean, dueDate: String
): SplitPaymentValidation {
    val paidOnly = validateSplitPayment(total, cash, card, transfer, "", true, null, true, "")
    if (paidOnly.error != null) return paidOnly
    if (paidOnly.sum > total) return paidOnly.copy(error = "El abono no puede superar el total de la venta.", canSubmit = false)
    val pending = if (useCredit) total - paidOnly.sum else 0L
    return validateSplitPayment(total, cash, card, transfer, BigDecimal.valueOf(pending, 2).toPlainString(),
        customerSelected, creditAvailable, creditEnabled, dueDate)
}

internal data class SplitPaymentValidation(
    val payments: List<PosPaymentSplitInput> = emptyList(),
    val sum: Long = 0,
    val difference: Long = 0,
    val credit: Long = 0,
    val dueDate: String? = null,
    val error: String? = null,
    val canSubmit: Boolean = false
)

internal fun validateSplitPayment(
    total: Long, cash: String, card: String, transfer: String, credit: String,
    customerSelected: Boolean, creditAvailable: Long?, creditEnabled: Boolean, dueDate: String
): SplitPaymentValidation {
    val methods = listOf("cash", "card", "bank_transfer", "credit")
    val inputs = listOf(cash, card, transfer, credit)
    val amounts = inputs.map { raw ->
        val text = raw.trim()
        if (text.isEmpty()) 0L else MoneyUtils.parseDecimalToCents(
            if (text.matches(Regex("[0-9]{1,3}(,[0-9]{3})+(\\.[0-9]{1,2})?"))) text.replace(",", "") else text
        )
    }
    if (amounts.any { it == null }) return SplitPaymentValidation(error = "Revisa los importes: usa números positivos con un máximo de dos decimales.")
    val values = amounts.map { requireNotNull(it) }
    val sum = runCatching { values.fold(0L, Math::addExact) }.getOrNull()
        ?: return SplitPaymentValidation(error = "La suma de los pagos excede el importe permitido.")
    val creditCents = values[3]
    val normalizedDate = dueDate.trim().takeIf { creditCents > 0 && it.isNotEmpty() }
    val error = when {
        total <= 0 -> "Agrega productos antes de cobrar."
        creditCents > 0 && !creditEnabled -> "Tu rol no tiene habilitadas las ventas a crédito."
        creditCents > 0 && !customerSelected -> "Selecciona un cliente para la parte a crédito."
        creditCents > 0 && creditAvailable != null && creditCents > creditAvailable -> "El importe a crédito supera el crédito disponible del cliente."
        normalizedDate != null && runCatching { LocalDate.parse(normalizedDate) }.isFailure -> "La fecha de vencimiento debe ser válida y tener formato AAAA-MM-DD."
        else -> null
    }
    return SplitPaymentValidation(
        payments = methods.zip(values).filter { it.second > 0 }.map { PosPaymentSplitInput(it.first, it.second) },
        sum = sum, difference = total - sum, credit = creditCents, dueDate = normalizedDate,
        error = error, canSubmit = error == null && sum == total && total > 0
    )
}
