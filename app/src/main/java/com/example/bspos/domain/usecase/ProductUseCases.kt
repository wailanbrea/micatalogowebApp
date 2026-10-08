package com.example.bspos.domain.usecase

import com.example.bspos.domain.model.Product
import com.example.bspos.domain.model.ProductComboComponent
import com.example.bspos.domain.repository.CategoryRepository
import com.example.bspos.domain.repository.ProductRepository
import com.example.bspos.domain.repository.UnitOfMeasureRepository
import kotlinx.coroutines.flow.Flow
import java.time.Instant
import java.util.UUID
import javax.inject.Inject

data class ProductInput(
    val name: String,
    val internalCode: String,
    val categoryId: UUID,
    val unitId: UUID,
    val salePrice: Long,
    val purchasePrice: Long = 0,
    val wholesalePrice: Long? = null,
    val description: String? = null,
    val minimumStock: Long = 0,
    val tracksExpiration: Boolean = false,
    val imagePath: String? = null,
    val thumbnailPath: String? = null,
    val remoteShopId: String? = null,
    val remoteSaleUnit: String? = null,
    val remoteVolumeMl: Int? = null,
    val remoteSourceProductId: String? = null,
    val isCombo: Boolean = false,
    val comboItems: List<ProductComboComponent> = emptyList(),
    val isActive: Boolean = true
)

class ProductUseCases @Inject constructor(
    private val products: ProductRepository,
    private val categories: CategoryRepository,
    private val units: UnitOfMeasureRepository
) {
    fun observe(): Flow<List<Product>> = products.observeAll()

    suspend fun create(input: ProductInput): Product {
        val now = Instant.now()
        val product = build(UUID.randomUUID(), input, now, now)
        products.insert(product)
        return product
    }

    suspend fun update(current: Product, input: ProductInput): Boolean = products.update(
        build(current.id, input, current.createdAt, Instant.now()).copy(
            averageCost = current.averageCost,
            description = input.description ?: current.description,
            wholesalePrice = input.wholesalePrice ?: current.wholesalePrice,
            barcode = current.barcode,
            remoteShopId = current.remoteShopId,
            remoteProductId = current.remoteProductId,
            remoteSaleUnit = input.remoteSaleUnit ?: current.remoteSaleUnit,
            remoteIsCombo = input.isCombo,
            remoteComboItems = input.comboItems,
            remoteVolumeMl = if (input.remoteSaleUnit != null) input.remoteVolumeMl else current.remoteVolumeMl,
            remoteAvailableMl = current.remoteAvailableMl,
            remoteOpenedBottles = current.remoteOpenedBottles,
            remoteSourceProductId = if (input.remoteSaleUnit != null) input.remoteSourceProductId else current.remoteSourceProductId,
            lastPurchaseCost = current.lastPurchaseCost,
            imagePath = input.imagePath ?: current.imagePath,
            thumbnailPath = input.thumbnailPath ?: current.thumbnailPath,
            isActive = input.isActive,
            deletedAt = current.deletedAt
        )
    )

    suspend fun delete(id: UUID): Boolean = products.softDelete(id, Instant.now())

    private suspend fun build(id: UUID, input: ProductInput, createdAt: Instant, updatedAt: Instant): Product {
        val category = checkNotNull(categories.findById(input.categoryId)) { "Category not found" }
        val unit = checkNotNull(units.findById(input.unitId)) { "Unit not found" }
        require(category.isActive && category.deletedAt == null && unit.isActive && unit.deletedAt == null)
        require(input.salePrice >= 0 && input.purchasePrice >= 0 && input.minimumStock >= 0 && input.wholesalePrice?.let { it >= 0 } != false)
        return Product(
            id = id,
            name = input.name.clean("Product name"),
            internalCode = input.internalCode.clean("Internal code").uppercase(),
            categoryId = input.categoryId,
            unitId = input.unitId,
            description = input.description.cleanOptional(),
            salePrice = input.salePrice,
            averageCost = input.purchasePrice,
            lastPurchaseCost = input.purchasePrice,
            wholesalePrice = input.wholesalePrice,
            minimumStock = input.minimumStock,
            tracksExpiration = input.tracksExpiration,
            imagePath = input.imagePath,
            thumbnailPath = input.thumbnailPath,
            createdAt = createdAt,
            updatedAt = updatedAt,
            remoteShopId = input.remoteShopId,
            remoteProductId = input.remoteShopId?.let { com.example.bspos.data.micatalogo.RemoteMutationRecorder.productId(id) },
            remoteSaleUnit = input.remoteSaleUnit,
            remoteVolumeMl = input.remoteVolumeMl,
            remoteSourceProductId = input.remoteSourceProductId,
            remoteIsCombo = input.isCombo,
            remoteComboItems = input.comboItems,
            isActive = input.isActive
        )
    }
}
