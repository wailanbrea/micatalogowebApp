package com.example.bspos.presentation.pos

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.bspos.core.ui.theme.BSPOSTheme
import com.example.bspos.domain.model.Product
import java.time.Instant
import java.util.UUID
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ProductGridScrollTest {
    @get:Rule val compose = createComposeRule()

    @Test fun productGridCanScrollToItemsBeyondTheInitialViewport() {
        val now = Instant.parse("2026-10-07T12:00:00Z")
        val category = UUID.randomUUID()
        val unit = UUID.randomUUID()
        val products = (0 until 20).map { index ->
            Product(UUID.randomUUID(), "Terminal product $index", "SKU-$index", categoryId = category, unitId = unit, salePrice = 10000L, createdAt = now, updatedAt = now)
        }
        compose.setContent {
            BSPOSTheme {
                ProductGrid(products, emptyMap(), emptyMap(), true, {}, { _, _ -> }, 2, modifier = Modifier.fillMaxSize().padding(12.dp).height(360.dp))
            }
        }
        compose.onNodeWithText("Terminal product 0").assertIsDisplayed()
        compose.onNodeWithText("Terminal product 19").performScrollTo().assertIsDisplayed()
    }
}
