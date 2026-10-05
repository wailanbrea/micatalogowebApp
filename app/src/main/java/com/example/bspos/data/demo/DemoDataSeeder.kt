package com.example.bspos.data.demo

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import com.example.bspos.core.database.AppDatabaseTransactor
import com.example.bspos.domain.model.Category
import com.example.bspos.domain.model.CommercialRoute
import com.example.bspos.domain.model.Customer
import com.example.bspos.domain.model.InventoryLocation
import com.example.bspos.domain.model.InventoryMovement
import com.example.bspos.domain.model.InventoryMovementType
import com.example.bspos.domain.model.Product
import com.example.bspos.domain.model.RouteCustomer
import com.example.bspos.domain.model.SalePaymentType
import com.example.bspos.domain.model.UnitOfMeasure
import com.example.bspos.domain.usecase.CompleteSaleRequest
import com.example.bspos.domain.usecase.CompleteSaleUseCase
import com.example.bspos.domain.usecase.SaleLineInput
import com.example.bspos.domain.repository.CategoryRepository
import com.example.bspos.domain.repository.CustomerRepository
import com.example.bspos.domain.repository.InventoryRepository
import com.example.bspos.domain.repository.ProductRepository
import com.example.bspos.domain.repository.RouteRepository
import com.example.bspos.domain.repository.SaleRepository
import com.example.bspos.domain.repository.UnitOfMeasureRepository
import com.example.bspos.domain.repository.MiCatalogoConnectionRepository
import kotlinx.coroutines.flow.first
import java.time.Duration
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Loads a small, deterministic offline catalog the first time the app has an empty database.
 * The seed is deliberately made of normal domain records so it exercises the same constraints
 * and inventory ledger used by the real screens.
 */
@Singleton
class DemoDataSeeder @Inject constructor(
    private val transactor: AppDatabaseTransactor,
    private val categories: CategoryRepository,
    private val units: UnitOfMeasureRepository,
    private val products: ProductRepository,
    private val customers: CustomerRepository,
    private val routes: RouteRepository,
    private val inventory: InventoryRepository,
    private val sales: SaleRepository,
    private val completeSale: CompleteSaleUseCase,
    private val dataStore: DataStore<Preferences>,
    private val miCatalogoConnection: MiCatalogoConnectionRepository
) {
    suspend fun seedIfNeeded() {
        if (miCatalogoConnection.observeConnection().first().isConfigured) return
        if (dataStore.data.first()[DEMO_DATA_INITIALIZED] != true) {
            val seeded = transactor.runInTransaction {
                if (products.observeAll().first().isNotEmpty()) {
                    false
                } else {
                    seedCatalogAndCustomers()
                    true
                }
            }

            // Mark both a successful seed and an already-used database. This prevents a later
            // deletion of all products from unexpectedly recreating demo data.
            if (seeded || products.observeAll().first().isNotEmpty()) {
                dataStore.edit { it[DEMO_DATA_INITIALIZED] = true }
            }
        }

        seedRecentSalesIfNeeded()
    }

    private suspend fun seedRecentSalesIfNeeded() {
        if (dataStore.data.first()[DEMO_SALES_INITIALIZED] == true) return
        if (sales.observeAll().first().any { it.invoiceNumber.startsWith(DEMO_INVOICE_PREFIX) }) {
            dataStore.edit { it[DEMO_SALES_INITIALIZED] = true }
            return
        }

        val seeded = transactor.runInTransaction {
            if (sales.observeAll().first().any { it.invoiceNumber.startsWith(DEMO_INVOICE_PREFIX) }) {
                false
            } else {
                seedRecentSales()
                true
            }
        }
        if (seeded) dataStore.edit { it[DEMO_SALES_INITIALIZED] = true }
    }

    private suspend fun seedRecentSales() {
        val demoProducts = listOf(
            products.findById(stableId("agua-15")),
            products.findById(stableId("refresco-2l")),
            products.findById(stableId("jugo-mango")),
            products.findById(stableId("leche-1l")),
            products.findById(stableId("papas-150"))
        )
        val creditCustomer = customers.findById(stableId("cliente-esquina"))
        if (demoProducts.any { it == null } || creditCustomer == null) {
            dataStore.edit { it[DEMO_SALES_INITIALIZED] = true }
            return
        }
        val water = demoProducts[0]!!
        val soda = demoProducts[1]!!
        val juice = demoProducts[2]!!
        val milk = demoProducts[3]!!
        val chips = demoProducts[4]!!
        val now = Instant.now()
        val latestInitialStock = listOf(water, soda, juice, milk, chips)
            .mapNotNull { inventory.findStock(it.id, InventoryLocation.MAIN)?.updatedAt }
            .maxOrNull()
            ?: now.minus(4, ChronoUnit.DAYS)
        val firstSaleDate = maxOf(now.minus(4, ChronoUnit.DAYS), latestInitialStock.plusSeconds(1))
        val intervalSeconds = (Duration.between(firstSaleDate, now).seconds.coerceAtLeast(4)) / 4
        val saleDates = (0..4).map { firstSaleDate.plusSeconds(intervalSeconds * it) }

        // These are real completed sales, so the dashboard totals, Kardex and customer balance
        // all come from the same production rules used by POS.
        listOf(
            DemoSale("DEMO-0001", saleDates[4], chips, 12, SalePaymentType.CARD),
            DemoSale("DEMO-0002", saleDates[3], water, 4, SalePaymentType.TRANSFER),
            DemoSale("DEMO-0003", saleDates[2], soda, 2, SalePaymentType.CARD),
            DemoSale("DEMO-0004", saleDates[1], juice, 3, SalePaymentType.TRANSFER),
            DemoSale("DEMO-0005", saleDates[0], milk, 2, SalePaymentType.CREDIT, creditCustomer.id)
        ).forEach { demo ->
            val total = Math.multiplyExact(demo.quantity, demo.product.salePrice)
            completeSale(
                CompleteSaleRequest(
                    invoiceNumber = demo.invoiceNumber,
                    customerId = demo.customerId,
                    date = demo.date,
                    lines = listOf(SaleLineInput(demo.product.id, demo.quantity, demo.product.salePrice)),
                    paymentType = demo.paymentType,
                    paidAmount = if (demo.paymentType == SalePaymentType.CREDIT) 0 else total,
                    pendingAmount = if (demo.paymentType == SalePaymentType.CREDIT) total else 0,
                    notes = "Venta demo para mostrar el resumen del negocio"
                )
            )
        }
    }

    private suspend fun seedCatalogAndCustomers() {
        val now = Instant.now()
        val inventoryAt = now.minus(5, ChronoUnit.DAYS)
        val categoryBeverages = category("bebidas", "Bebidas", "Bebidas frías y jugos", 0, now)
        val categoryGroceries = category("despensa", "Despensa", "Productos básicos", 1, now)
        val categorySnacks = category("snacks", "Snacks", "Galletas y picaderas", 2, now)
        val categoryDairy = category("lacteos", "Lácteos", "Leche y derivados", 3, now)
        listOf(categoryBeverages, categoryGroceries, categorySnacks, categoryDairy).forEach { categories.insert(it) }

        val unitBottle = unit("botella", "Botella", "bot", now)
        val unitUnit = unit("unidad", "Unidad", "ud", now)
        val unitPackage = unit("paquete", "Paquete", "paq", now)
        listOf(unitBottle, unitUnit, unitPackage).forEach { units.insert(it) }

        val seedProducts = listOf(
            SeedProduct("agua-15", "Agua Purificada 1.5L", "AGUA-15", categoryBeverages.id, unitBottle.id, 350, 180, 24, "demo_water", 48),
            SeedProduct("refresco-2l", "Refresco Cola 2L", "REF-COLA-2L", categoryBeverages.id, unitBottle.id, 750, 430, 12, "demo_soda", 30),
            SeedProduct("jugo-mango", "Jugo de Mango 1L", "JUGO-MANGO-1L", categoryBeverages.id, unitBottle.id, 625, 360, 12, "demo_juice", 24),
            SeedProduct("leche-1l", "Leche Entera 1L", "LECHE-1L", categoryDairy.id, unitBottle.id, 850, 560, 10, "demo_milk", 20),
            SeedProduct("papas-150", "Papas Fritas 150g", "PAPAS-150G", categorySnacks.id, unitPackage.id, 425, 245, 8, "demo_chips", 18),
            SeedProduct("galletas-choco", "Galletas de Chocolate", "GALL-CHOCO", categorySnacks.id, unitPackage.id, 300, 165, 8, "demo_cookie", 24),
            SeedProduct("arroz-1lb", "Arroz Premium 1 lb", "ARROZ-1LB", categoryGroceries.id, unitUnit.id, 950, 680, 10, "demo_rice", 25)
        )
        val seededProducts = seedProducts.map { seed ->
            Product(
                id = stableId(seed.key),
                name = seed.name,
                internalCode = seed.code,
                barcode = "74612345${seed.code.hashCode().toString().filter { it.isDigit() }.padStart(4, '0').takeLast(4)}",
                categoryId = seed.categoryId,
                unitId = seed.unitId,
                description = "Producto de demostración para MiCatalogo",
                salePrice = seed.salePrice,
                wholesalePrice = seed.salePrice - 25,
                averageCost = seed.cost,
                lastPurchaseCost = seed.cost,
                minimumStock = seed.minimumStock,
                imagePath = localImage(seed.imageName),
                thumbnailPath = localImage(seed.imageName),
                createdAt = inventoryAt,
                updatedAt = inventoryAt
            )
        }
        seededProducts.forEach { products.insert(it) }

        val route = CommercialRoute(
            id = stableId("ruta-centro"),
            name = "Ruta Centro",
            code = "R-001",
            description = "Clientes de demostración del centro",
            createdAt = inventoryAt,
            updatedAt = inventoryAt
        )
        routes.insert(route)

        val seededCustomers = listOf(
            Customer(stableId("cliente-esquina"), "Colmado La Esquina", firstName = "María", lastName = "Rodríguez", documentType = "cedula", documentNumber = "001-0000000-1", phone = "809-555-0101", whatsapp = "809-555-0101", address = "Av. Independencia #12", reference = "Frente al parque", creditLimit = 15000, createdAt = inventoryAt, updatedAt = inventoryAt),
            Customer(stableId("cliente-familia"), "Mini Market La Familia", firstName = "José", lastName = "Martínez", documentType = "cedula", documentNumber = "001-0000000-2", phone = "809-555-0102", whatsapp = "809-555-0102", address = "Calle Duarte #45", reference = "Junto a la farmacia", creditLimit = 25000, createdAt = inventoryAt, updatedAt = inventoryAt),
            Customer(stableId("cliente-playa"), "Bodega Playa", firstName = "Ana", lastName = "Pérez", documentType = "cedula", documentNumber = "001-0000000-3", phone = "809-555-0103", whatsapp = "809-555-0103", address = "Carretera Sánchez km 3", reference = "Local azul", creditLimit = 10000, createdAt = inventoryAt, updatedAt = inventoryAt)
        )
        seededCustomers.forEach { customers.insert(it) }
        seededCustomers.forEachIndexed { index, customer -> routes.assign(RouteCustomer(route.id, customer.id, index)) }

        val movements = seededProducts.zip(seedProducts).map { (product, seed) ->
            val quantity = seed.initialQuantity
            InventoryMovement(
                id = stableId("initial-${product.internalCode}"),
                productId = product.id,
                location = InventoryLocation.MAIN,
                type = InventoryMovementType.INITIAL,
                quantity = quantity,
                previousQuantity = 0,
                newQuantity = quantity,
                unitCost = product.averageCost,
                totalCost = Math.multiplyExact(quantity, product.averageCost),
                notes = "Inventario inicial de demostración",
                createdAt = inventoryAt
            )
        }
        inventory.recordMovements(movements)
    }

    private fun category(key: String, name: String, description: String, sortOrder: Int, now: Instant) =
        Category(stableId("category-$key"), name, description, sortOrder = sortOrder, createdAt = now, updatedAt = now)

    private fun unit(key: String, name: String, abbreviation: String, now: Instant) =
        UnitOfMeasure(stableId("unit-$key"), name, abbreviation, createdAt = now, updatedAt = now)

    private fun stableId(key: String): UUID = UUID.nameUUIDFromBytes("bspos-demo:$key".toByteArray())

    private fun localImage(name: String): String = "android.resource://com.example.bspos/drawable/$name"

    private data class SeedProduct(
        val key: String,
        val name: String,
        val code: String,
        val categoryId: UUID,
        val unitId: UUID,
        val salePrice: Long,
        val cost: Long,
        val minimumStock: Long,
        val imageName: String,
        val initialQuantity: Long
    )

    private data class DemoSale(
        val invoiceNumber: String,
        val date: Instant,
        val product: Product,
        val quantity: Long,
        val paymentType: SalePaymentType,
        val customerId: UUID? = null
    )

    private companion object {
        val DEMO_DATA_INITIALIZED = booleanPreferencesKey("demo_data_initialized")
        val DEMO_SALES_INITIALIZED = booleanPreferencesKey("demo_sales_initialized")
        const val DEMO_INVOICE_PREFIX = "DEMO-"
    }
}
