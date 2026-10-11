package com.example.bspos.presentation.customer

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.bspos.core.ui.theme.BSPOSTheme
import com.example.bspos.data.micatalogo.dto.*
import com.example.bspos.domain.model.Customer
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Instant
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class CustomerAccountScreenTest {
    @get:Rule val compose=createComposeRule()
    private val customer=Customer(UUID.randomUUID(),"Cliente QA",phone="8095550101",balance=20000,creditLimit=100000,
        createdAt=Instant.now(),updatedAt=Instant.now())

    @Test fun compactCardOpensDetailsAndActionsDoNotOpenTheCard() {
        var opened=0
        var edited=0
        compose.setContent { BSPOSTheme { CustomerCard(customer,true,{opened++},{edited++},{}) } }
        compose.onNodeWithText("Cliente QA").performClick()
        assertEquals(1,opened)
        compose.onNodeWithContentDescription("Acciones de Cliente QA").performClick()
        compose.onNodeWithText("Editar cliente").performClick()
        assertEquals(1,edited)
        assertEquals(1,opened)
        compose.onNodeWithText("Pendiente").assertExists()
    }

    @Test fun accountShowsInvoiceOriginAndPaymentsWithRemainingBalance() {
        val account=CustomerAccountDetailDto(RemoteCustomerDto("remote",balance="200.00",creditLimit="1000.00"),
            debtOrigins=listOf(CustomerDebtOriginDto("FAC-600","PED-1","whatsapp",total="600.00",paidAmount="400.00",outstandingAmount="200.00")),
            entries=listOf(CustomerAccountEntryDto("1","payment","-200.00","200.00",createdAt=Instant.now().toString(),
                allocations=listOf(CustomerEntryAllocationDto("FAC-600","200.00")))))
        compose.setContent { BSPOSTheme { CustomerAccountScreen(CustomerDetailState(customer,false,account),{},{}) } }
        compose.onNodeWithText("Origen de la deuda").assertExists()
        compose.onNodeWithText("FAC-600").assertExists()
        compose.onNodeWithText("Pedido PED-1 · Pedido WhatsApp").assertExists()
        compose.onNode(hasScrollAction()).performScrollToNode(hasText("Abono recibido"))
        compose.onNodeWithText("Abono recibido").assertExists()
        compose.onNodeWithText("-RD$ 200.00").assertExists()
        compose.onNodeWithText("Aplicado a FAC-600: RD$ 200.00").assertExists()
    }

    @Test fun aCustomerWithoutDebtStillShowsAnEmptyMovementHistory() {
        val account=CustomerAccountDetailDto(RemoteCustomerDto("remote",balance="0.00",creditLimit="1000.00"))
        compose.setContent { BSPOSTheme { CustomerAccountScreen(CustomerDetailState(customer,false,account),{},{}) } }
        compose.onNodeWithText("No tiene deuda pendiente.").assertExists()
        compose.onNodeWithText("Este cliente todavía no tiene movimientos registrados.").assertExists()
    }

    @Test fun loadingAndFailureNeverPretendThereAreNoMovements() {
        compose.setContent { BSPOSTheme { CustomerAccountScreen(CustomerDetailState(customer,error="Sin conexión",loading=false),{},{}) } }
        compose.onNodeWithText("Sin conexión").assertExists()
        compose.onNodeWithText("Reintentar").assertExists()
        compose.onNodeWithText("Este cliente todavía no tiene movimientos registrados.").assertDoesNotExist()
    }
}
