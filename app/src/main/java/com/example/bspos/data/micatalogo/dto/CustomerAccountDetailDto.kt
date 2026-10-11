package com.example.bspos.data.micatalogo.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class CustomerAccountDetailDto(
    val customer:RemoteCustomerDto,
    val entries:List<CustomerAccountEntryDto> = emptyList(),
    @SerialName("entries_has_more") val entriesHasMore:Boolean=false,
    @SerialName("debt_origins") val debtOrigins:List<CustomerDebtOriginDto> = emptyList(),
    @SerialName("unallocated_balance") val unallocatedBalance:String?=null
)

@Serializable
data class CustomerAccountEntryDto(
    val id:String,val type:String,val amount:String,
    @SerialName("balance_after") val balanceAfter:String,
    @SerialName("invoice_number") val invoiceNumber:String?=null,
    val notes:String?=null,
    @SerialName("created_at") val createdAt:String,
    val allocations:List<CustomerEntryAllocationDto> = emptyList()
)

@Serializable
data class CustomerEntryAllocationDto(
    @SerialName("invoice_number") val invoiceNumber:String?=null,val amount:String
)

@Serializable
data class CustomerDebtOriginDto(
    @SerialName("invoice_number") val invoiceNumber:String,
    @SerialName("order_number") val orderNumber:String?=null,
    val channel:String?=null,
    @SerialName("issued_at") val issuedAt:String?=null,
    @SerialName("due_date") val dueDate:String?=null,
    val total:String,
    @SerialName("paid_amount") val paidAmount:String="0.00",
    @SerialName("returned_amount") val returnedAmount:String="0.00",
    @SerialName("outstanding_amount") val outstandingAmount:String
)
