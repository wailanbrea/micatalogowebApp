package com.example.bspos.presentation.practice

/** Isolated training values. No identifiers, repositories, database or network access. */
internal data class PracticeSession(
    val stock: Int = 10,
    val cashCents: Long = 0,
    val debtCents: Long = 0,
    val salesCents: Long = 0,
    val profitCents: Long = 0
) {
    fun sell(quantity: Int, credit: Boolean): PracticeSession {
        require(quantity > 0 && quantity <= stock) { "No alcanza la existencia de práctica." }
        val total = Math.multiplyExact(quantity.toLong(), PRICE_CENTS)
        return copy(stock = stock - quantity,
            salesCents = Math.addExact(salesCents, total),
            profitCents = Math.addExact(profitCents, Math.multiplyExact(quantity.toLong(), PRICE_CENTS - COST_CENTS)),
            cashCents = if (credit) cashCents else Math.addExact(cashCents, total),
            debtCents = if (credit) Math.addExact(debtCents, total) else debtCents)
    }

    fun collect(amountCents: Long): PracticeSession {
        require(amountCents > 0 && amountCents <= debtCents) { "El cobro debe estar dentro del saldo de práctica." }
        return copy(debtCents = debtCents - amountCents, cashCents = Math.addExact(cashCents, amountCents))
    }

    fun receive(quantity: Int): PracticeSession {
        require(quantity in 1..1000) { "Indica de 1 a 1000 unidades de práctica." }
        return copy(stock = Math.addExact(stock, quantity))
    }

    companion object {
        const val PRICE_CENTS = 30000L
        const val COST_CENTS = 12000L
    }
}
