package com.example.bspos.presentation.startup

/**
 * A local sale may complete a new-shop milestone while the remote snapshot
 * still says that the shop had no sales when startup began.
 */
internal fun resolveFirstSaleStatus(bootstrapStatus: Boolean?, localHasSale: Boolean): Boolean? = when {
    bootstrapStatus == true -> true
    bootstrapStatus == false && localHasSale -> true
    bootstrapStatus == false -> false
    else -> null
}
