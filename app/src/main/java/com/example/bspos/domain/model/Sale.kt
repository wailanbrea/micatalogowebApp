package com.example.bspos.domain.model

import java.time.Instant
import java.util.UUID

enum class SalePaymentType { CASH, CREDIT, CARD, TRANSFER, MIXED }
enum class SaleStatus { COMPLETED, CANCELLED }
enum class PosSaleOutboxState { PENDING, RETRY, SENT, BLOCKED }
data class Sale(val id: UUID, val invoiceNumber: String, val customerId: UUID? = null, val routeId: UUID? = null, val date: Instant, val subtotal: Long, val discount: Long = 0, val tax: Long = 0, val total: Long, val paymentType: SalePaymentType, val paidAmount: Long = 0, val pendingAmount: Long = 0, val status: SaleStatus = SaleStatus.COMPLETED, val notes: String? = null, val createdAt: Instant, val updatedAt: Instant, val saleMode: String = "retail")
data class SaleItem(val id: UUID, val saleId: UUID, val productId: UUID, val quantity: Long, val unitPrice: Long, val unitCostSnapshot: Long, val discount: Long = 0, val tax: Long = 0, val subtotal: Long)
