package com.example.bspos.core.money

import com.example.bspos.domain.model.CurrencyUnit
import java.math.BigDecimal
import java.math.RoundingMode
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Locale

/**
 * Utilidades monetarias de alta precisión para BSPOS.
 * Todo el dinero se modela como centavos en Long.
 * Prohibido el uso de Float o Double para operaciones monetarias.
 */
object MoneyUtils {
    private val dominicanSymbols = DecimalFormatSymbols(Locale.forLanguageTag("es-DO")).apply {
        decimalSeparator = '.'
        groupingSeparator = ','
    }

    private val dominicanFormat = DecimalFormat("RD$ #,##0.00", dominicanSymbols)
    private val dollarFormat = DecimalFormat("US$ #,##0.00", dominicanSymbols)
    private val compactFormat = DecimalFormat("#,##0.00", dominicanSymbols)

    fun formatCents(cents: Long, currency: CurrencyUnit = CurrencyUnit.DOP): String {
        val pesos = BigDecimal(cents).divide(BigDecimal(100), 2, RoundingMode.HALF_EVEN)
        return (if (currency == CurrencyUnit.USD) dollarFormat else dominicanFormat).format(pesos)
    }

    fun formatCentsCompact(cents: Long): String {
        val pesos = BigDecimal(cents).divide(BigDecimal(100), 2, RoundingMode.HALF_EVEN)
        return compactFormat.format(pesos)
    }

    fun pesosToCents(pesos: Double): Long {
        return BigDecimal.valueOf(pesos)
            .multiply(BigDecimal(100))
            .setScale(0, RoundingMode.HALF_EVEN)
            .toLong()
    }

    fun parsePesosStringToCents(text: String): Long {
        val clean = text.replace("[^0-9.]".toRegex(), "")
        if (clean.isBlank()) return 0L
        return try {
            BigDecimal(clean)
                .multiply(BigDecimal(100))
                .setScale(0, RoundingMode.HALF_EVEN)
                .toLong()
        } catch (e: Exception) {
            0L
        }
    }

    fun parseWholeUnitsToCents(text: String): Long? = runCatching {
        Math.multiplyExact(text.filter(Char::isDigit).toLongOrNull() ?: return null, 100L)
    }.getOrNull()

    fun parseDecimalToCents(text: String): Long? = runCatching {
        val value = text.trim().replace(',', '.')
        require(value.matches(Regex("[0-9]+(\\.[0-9]{1,2})?")))
        BigDecimal(value).movePointRight(2).longValueExact()
    }.getOrNull()
}

fun Long.toPesosFormatted(currency: CurrencyUnit = CurrencyUnit.DOP): String = MoneyUtils.formatCents(this, currency)
fun Long.toPesosCompact(): String = MoneyUtils.formatCentsCompact(this)
