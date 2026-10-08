package com.example.bspos.data.micatalogo.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class SellerSummaryDto(
    val period: String,
    @SerialName("period_label") val periodLabel: String,
    val metrics: SellerSummaryMetricsDto,
    val chart: List<SellerSummaryDayDto>,
    val sales: List<SellerSummarySaleDto>,
    @SerialName("has_more_sales") val hasMoreSales: Boolean = false
)

@Serializable
data class SellerSummaryMetricsDto(val count: Int, val total: Long, val commission: Long, val average: Long)

@Serializable
data class SellerSummaryDayDto(val label: String, val total: Long)

@Serializable
data class SellerSummarySaleDto(
    @SerialName("invoice_number") val invoiceNumber: String,
    val customer: String,
    val date: String,
    val total: Long,
    val commission: Long,
    val status: String
)
