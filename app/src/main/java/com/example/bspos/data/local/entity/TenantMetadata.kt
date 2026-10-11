package com.example.bspos.data.local.entity

import androidx.room.ColumnInfo

/** NULL ownership is preserved history, never an operational wildcard. */
data class TenantMetadata(
    @ColumnInfo(name = "remote_shop_id") val shopId: String? = null,
    @ColumnInfo(name = "tenancy_status", defaultValue = "'legacy_ambiguous'")
    val status: String = if (shopId.isNullOrBlank()) AMBIGUOUS else ASSIGNED
) {
    companion object {
        const val AMBIGUOUS = "legacy_ambiguous"
        const val ASSIGNED = "assigned"
        fun assigned(shopId: String) = TenantMetadata(shopId.also { require(it.isNotBlank()) }, ASSIGNED)
    }
}
