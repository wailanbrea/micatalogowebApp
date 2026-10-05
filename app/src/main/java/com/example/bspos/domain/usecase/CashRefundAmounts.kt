package com.example.bspos.domain.usecase

import java.math.BigInteger

data class RefundableLine(val id: String, val quantity: Long, val subtotal: Long)

/** The input order is the original invoice line order, not a display sort. */
object CashRefundAmounts {
    fun allocate(lines: List<RefundableLine>, discount: Long, tax: Long): Map<String, Long> {
        require(lines.isNotEmpty() && lines.map { it.id }.distinct().size == lines.size)
        require(discount >= 0 && tax >= 0 && lines.all { it.quantity > 0 && it.subtotal >= 0 })
        var remainingSubtotal = lines.fold(0L) { sum, line -> Math.addExact(sum, line.subtotal) }
        require(discount <= remainingSubtotal)
        var remainingDiscount = discount
        val net = lines.associate { line ->
            val allocated = if (remainingSubtotal == 0L) 0L else mulDiv(remainingDiscount, line.subtotal, remainingSubtotal)
            remainingDiscount -= allocated
            remainingSubtotal -= line.subtotal
            line.id to (line.subtotal - allocated)
        }
        val netTotal = net.values.fold(0L, Math::addExact)
        // With a fully discounted invoice, distribute tax by original subtotal;
        // if all lines are free, use quantities. This still conserves every cent.
        val subtotalTotal = lines.fold(0L) { sum, line -> Math.addExact(sum, line.subtotal) }
        val weights = lines.associate { it.id to when { netTotal > 0 -> net.getValue(it.id); subtotalTotal > 0 -> it.subtotal; else -> it.quantity } }
        val weightTotal = weights.values.fold(0L, Math::addExact)
        var cumulative = 0L
        var assignedTax = 0L
        return lines.associate { line ->
            cumulative = Math.addExact(cumulative, weights.getValue(line.id))
            val nextTax = mulDiv(tax, cumulative, weightTotal)
            val share = nextTax - assignedTax
            assignedTax = nextTax
            line.id to Math.addExact(net.getValue(line.id), share)
        }
    }

    fun portion(charged: Long, sold: Long, alreadyReturned: Long, quantity: Long): Long {
        require(charged >= 0 && sold > 0 && alreadyReturned >= 0 && quantity > 0)
        val cumulative = Math.addExact(alreadyReturned, quantity)
        require(cumulative <= sold) { "La devolución supera las unidades vendidas." }
        return mulDiv(charged, cumulative, sold) - mulDiv(charged, alreadyReturned, sold)
    }

    private fun mulDiv(a: Long, b: Long, divisor: Long): Long =
        BigInteger.valueOf(a).multiply(BigInteger.valueOf(b)).divide(BigInteger.valueOf(divisor)).longValueExact()
}
