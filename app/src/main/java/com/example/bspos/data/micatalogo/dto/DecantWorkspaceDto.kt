package com.example.bspos.data.micatalogo.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class DecantWorkspaceDto(
    val enabled: Boolean = false,
    @SerialName("can_manage") val canManage: Boolean = false,
    val finance: Boolean = false,
    val period: String = "",
    val groups: List<DecantFragranceDto> = emptyList(),
    val vials: List<DecantVialDto> = emptyList(),
    val prepared: Int = 0,
    @SerialName("prepared_value_cents") val preparedValueCents: Long = 0,
    @SerialName("on_demand") val onDemand: Int = 0,
    val opened: Int = 0,
    @SerialName("lost_ml") val lostMl: Int = 0,
    @SerialName("unreconciled_ml") val unreconciledMl: Int = 0,
    @SerialName("empty_vials") val emptyVials: Int = 0,
    @SerialName("vial_investment_cents") val vialInvestmentCents: Long? = null,
    @SerialName("inventory_value_cents") val inventoryValueCents: Long? = null
)

@Serializable
data class DecantFragranceDto(
    val id: String,
    val name: String,
    val brand: String? = null,
    @SerialName("image_url") val imageUrl: String? = null,
    @SerialName("volume_ml") val volumeMl: Int = 0,
    val sealed: Int = 0,
    val tracked: Boolean = false,
    @SerialName("legacy_ml") val legacyMl: Int? = null,
    @SerialName("untracked_open_ml") val untrackedOpenMl: Int = 0,
    @SerialName("source_cost_cents") val sourceCostCents: Long? = null,
    val sizes: List<DecantSizeDto> = emptyList(),
    val openings: List<DecantOpeningDto> = emptyList()
)

@Serializable
data class DecantSizeDto(
    val id: String,
    val name: String,
    @SerialName("volume_ml") val volumeMl: Int,
    @SerialName("price_cents") val priceCents: Long = 0,
    val prepared: Int = 0,
    val capacity: Int = 0,
    @SerialName("legacy_capacity") val legacyCapacity: Int = 0,
    @SerialName("on_demand") val onDemand: Boolean = true,
    @SerialName("empty_vials") val emptyVials: Int = 0,
    @SerialName("vial_cost_cents") val vialCostCents: Long? = null,
    @SerialName("sold_month") val soldMonth: Int = 0,
    @SerialName("revenue_month_cents") val revenueMonthCents: Long = 0,
    @SerialName("profit_month_cents") val profitMonthCents: Long? = null,
    val offered: Boolean = true,
    val state: String = if (prepared > 0) "listo" else if (capacity > 0) "a_pedido" else "agotado",
    @SerialName("low_threshold") val lowThreshold: Int = 2,
    val low: Boolean = prepared in 1..lowThreshold,
    @SerialName("price_source") val priceSource: String = "catálogo"
)

@Serializable
data class DecantOpeningDto(
    val id: String,
    @SerialName("opened_at") val openedAt: String,
    @SerialName("initial_ml") val initialMl: Int,
    @SerialName("remaining_ml") val remainingMl: Int,
    @SerialName("lost_ml") val lostMl: Int = 0,
    @SerialName("cost_cents") val costCents: Long? = null,
    @SerialName("remaining_cost_cents") val remainingCostCents: Long? = null,
    @SerialName("revenue_cents") val revenueCents: Long = 0,
    @SerialName("can_undo") val canUndo: Boolean = false
)

@Serializable
data class DecantVialDto(
    val id: String,
    val name: String,
    @SerialName("volume_ml") val volumeMl: Int,
    val active: Boolean = true,
    val empty: Int = 0,
    @SerialName("investment_cents") val investmentCents: Long? = null,
    @SerialName("next_cost_cents") val nextCostCents: Long? = null,
    val lots: List<DecantVialLotDto> = emptyList()
)

@Serializable
data class DecantVialLotDto(
    val id: Long,
    @SerialName("received_at") val receivedAt: String,
    val received: Int,
    val remaining: Int,
    @SerialName("cost_cents") val costCents: Long? = null
)
