package com.example.bspos.presentation.collection

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.bspos.core.ui.theme.BSPOSTheme
import com.example.bspos.domain.model.Customer
import java.time.Instant
import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CustomerPickerTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun selectedCustomerCanBeReplacedWithAnotherCustomer() {
        val first = customer("Cliente uno")
        val second = customer("Cliente dos")
        var selectedId: UUID? = first.id

        compose.setContent {
            BSPOSTheme {
                CustomerPicker(
                    customers = listOf(first, second),
                    selectedId = first.id,
                    onSelect = { selectedId = it?.id },
                    onDismiss = {}
                )
            }
        }

        compose.onNodeWithText("Cliente dos").performClick()
        compose.runOnIdle { assertEquals(second.id, selectedId) }
    }

    private fun customer(name: String) = Customer(
        id = UUID.randomUUID(),
        businessName = name,
        creditLimit = 100_000,
        createdAt = Instant.EPOCH,
        updatedAt = Instant.EPOCH
    )
}
