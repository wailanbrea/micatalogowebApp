package com.example.bspos.domain.usecase

import com.example.bspos.data.micatalogo.api.CustomerPaymentDto
import com.example.bspos.data.micatalogo.dto.ExpenseCreateRequestDto
import com.example.bspos.domain.model.PaymentMethod
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Test

class CustomerPaymentApiMappingTest {
    @Test
    fun `maps local payment methods to the API contract`() {
        assertEquals("cash", PaymentMethod.CASH.toMiCatalogoPaymentMethod())
        assertEquals("card", PaymentMethod.CARD.toMiCatalogoPaymentMethod())
        assertEquals("bank_transfer", PaymentMethod.TRANSFER.toMiCatalogoPaymentMethod())
        assertEquals("other", PaymentMethod.CHECK.toMiCatalogoPaymentMethod())
    }

    @Test
    fun `serializes payment method and reference with the idempotent transaction`() {
        val body = Json.encodeToString(
            CustomerPaymentDto(
                uuid = "75c8b87c-1d1d-47d9-baa0-8c3317b1ee68",
                amount = "125.50",
                paymentMethod = PaymentMethod.TRANSFER.toMiCatalogoPaymentMethod(),
                reference = "TRX-104"
            )
        )

        val json = Json.parseToJsonElement(body).jsonObject
        assertEquals("75c8b87c-1d1d-47d9-baa0-8c3317b1ee68", json["client_transaction_uuid"]?.toString()?.trim('"'))
        assertEquals("bank_transfer", json["payment_method"]?.toString()?.trim('"'))
        assertEquals("TRX-104", json["reference"]?.toString()?.trim('"'))
        assertEquals("125.50", json["amount"]?.toString()?.trim('"'))
    }

    @Test
    fun `expense requests carry a server-recognized idempotency UUID`() {
        val body = Json.encodeToString(
            ExpenseCreateRequestDto(
                description = "Taxi",
                amount = "250.00",
                clientOperationUuid = "8595a0d1-faa3-41f0-af37-8929e9dd3116"
            )
        )

        val json = Json.parseToJsonElement(body).jsonObject
        assertEquals("8595a0d1-faa3-41f0-af37-8929e9dd3116", json["client_operation_uuid"]?.toString()?.trim('"'))
    }
}
