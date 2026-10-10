package com.example.bspos.presentation.orders

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.bspos.core.ui.theme.BSPOSTheme
import com.example.bspos.data.micatalogo.dto.FeatureRowDto
import com.example.bspos.data.micatalogo.dto.RemoteCustomerDto
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class OrderConfirmDialogTest {
    @get:Rule val compose = createComposeRule()

    @Test fun mixedTransferAsksForDownPaymentAndSendsTheCalculatedCredit() {
        var result: List<String?> = emptyList()
        val row = FeatureRowDto(id = "order-juan-pis", primary = "Pedido Juan Pis", value = "RD$ 9,500.00", canConfirm = true)
        val customer = RemoteCustomerDto(id = "customer-juan-pis", name = "Juan Pis", isActive = true)

        compose.setContent {
            BSPOSTheme {
                OrderConfirmDialog(
                    row = row,
                    customers = listOf(customer),
                    customersLoading = false,
                    busy = false,
                    onDismiss = {},
                    onConfirm = { kind, method, customerId, credit, reference ->
                        result = listOf(kind, method, customerId, credit, reference)
                    }
                )
            }
        }

        compose.onNodeWithText("Mixto").performClick()
        compose.onNodeWithText("Transferencia").performClick()
        compose.onNodeWithText("Abono inicial (RD$)").performTextReplacement("2000")
        compose.onNodeWithText("Saldo pendiente a crédito: RD$ 7,500.00").assertExists()
        compose.onNodeWithText("Juan Pis").performScrollTo().performClick()
        compose.onNodeWithText("Confirmar venta").assertIsEnabled().performClick()

        assertEquals(listOf("mixed", "bank_transfer", "customer-juan-pis", "7500.00", ""), result)
    }

    @Test fun creditModeOffersCreatingCustomerWithoutLeavingTheOrder() {
        var createClicks = 0
        val row = FeatureRowDto(id = "order-new-customer", primary = "Pedido nuevo cliente", value = "RD$ 600.00", canConfirm = true)

        compose.setContent {
            BSPOSTheme {
                OrderConfirmDialog(
                    row = row,
                    customers = emptyList(),
                    customersLoading = false,
                    busy = false,
                    onDismiss = {},
                    onCreateCustomer = { createClicks++ },
                    onConfirm = { _, _, _, _, _ -> }
                )
            }
        }

        compose.onNodeWithText("Crédito").performClick()
        compose.onNodeWithText("Nuevo cliente").assertIsDisplayed().performClick()

        assertEquals(1, createClicks)
        compose.onNodeWithText("Aún no hay clientes. Crea uno aquí para continuar a crédito.").assertExists()
    }

    @Test fun createdCustomerIsSelectedBeforeItsFormCloses() {
        var customerFormCloseCalls = 0
        var selectedCustomerId: String? = null
        val customer = RemoteCustomerDto(id = "customer-created", name = "Nuevo cliente", isActive = true)

        compose.setContent {
            BSPOSTheme {
                OrderConfirmDialog(
                    row = FeatureRowDto(id = "order-created-customer", primary = "Pedido", value = "RD$ 600.00", canConfirm = true),
                    customers = listOf(customer),
                    customersLoading = false,
                    createdCustomer = customer,
                    busy = false,
                    onDismiss = {},
                    onCreatedCustomerSelected = { customerFormCloseCalls++ },
                    onConfirm = { _, _, customerId, _, _ -> selectedCustomerId = customerId }
                )
            }
        }

        compose.waitForIdle()
        compose.onNodeWithText("Crédito").performClick()
        compose.onNodeWithText("Confirmar venta").assertIsEnabled().performClick()

        assertEquals(1, customerFormCloseCalls)
        assertEquals("customer-created", selectedCustomerId)
    }

    @Test fun creatingCustomerKeepsTheMixedOrderConfirmationOpen() {
        var orderOpen by mutableStateOf(true)
        var customerFormOpen by mutableStateOf(false)
        var createdCustomer by mutableStateOf<RemoteCustomerDto?>(null)
        val customer = RemoteCustomerDto(id = "customer-created", name = "Nuevo cliente", isActive = true)

        compose.setContent {
            BSPOSTheme {
                if (orderOpen) {
                    OrderConfirmDialog(
                        row = FeatureRowDto(id = "order-mixed", primary = "Pedido mixto", value = "RD$ 600.00", canConfirm = true),
                        customers = listOfNotNull(createdCustomer),
                        customersLoading = false,
                        createdCustomer = createdCustomer,
                        busy = false,
                        onDismiss = { orderOpen = false },
                        onCreateCustomer = { customerFormOpen = true },
                        onConsumeCreatedCustomer = { createdCustomer = null },
                        onCreatedCustomerSelected = { customerFormOpen = false },
                        onConfirm = { _, _, _, _, _ -> }
                    )
                }
                if (customerFormOpen) {
                    NewCreditCustomerDialog(
                        busy = false,
                        error = null,
                        onDismiss = { customerFormOpen = false },
                        onCreate = { createdCustomer = customer }
                    )
                }
            }
        }

        compose.onNodeWithText("Mixto").performClick()
        compose.onNodeWithText("Abono inicial (RD$)").performTextReplacement("100")
        compose.onNodeWithText("Nuevo cliente").performScrollTo().performClick()
        compose.waitUntil(5_000) { compose.onAllNodesWithText("Nombre completo").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Nombre completo").performTextReplacement("Nuevo cliente")
        compose.onNodeWithText("Límite de crédito (RD$)").performTextReplacement("1000")
        compose.onNodeWithText("Crear y seleccionar").performClick()

        compose.waitForIdle()
        assertEquals(true, orderOpen)
        compose.onNodeWithText("Confirmar pedido").assertExists()
        compose.onNodeWithText("Confirmar venta").assertIsEnabled()
    }
}
