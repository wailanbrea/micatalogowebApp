package com.example.bspos.data.micatalogo

import com.example.bspos.core.database.AppDatabaseTransactor
import com.example.bspos.data.local.dao.CategoryDao
import com.example.bspos.data.local.dao.InventoryDao
import com.example.bspos.data.local.dao.ProductDao
import com.example.bspos.data.local.dao.PosSaleOutboxDao
import com.example.bspos.data.local.dao.CustomerRouteDao
import com.example.bspos.data.local.dao.PaymentSyncDao
import com.example.bspos.data.local.dao.OperationOutboxDao
import com.example.bspos.data.local.entity.CustomerEntity
import com.example.bspos.data.micatalogo.api.MiCatalogoCustomerApi
import com.example.bspos.data.micatalogo.dto.RemoteCustomerDto
import com.example.bspos.data.local.dao.UnitOfMeasureDao
import com.example.bspos.data.local.entity.CategoryEntity
import com.example.bspos.data.local.entity.InventoryMovementEntity
import com.example.bspos.data.local.entity.ProductEntity
import com.example.bspos.data.local.entity.UnitOfMeasureEntity
import com.example.bspos.data.micatalogo.api.MiCatalogoApi
import com.example.bspos.data.micatalogo.dto.CatalogSnapshotDto
import com.example.bspos.data.micatalogo.dto.RemoteProductDto
import com.example.bspos.domain.model.InventoryLocation
import com.example.bspos.domain.model.InventoryLocationType
import com.example.bspos.domain.model.InventoryMovementType
import com.example.bspos.domain.model.MiCatalogoCatalogSyncResult
import com.example.bspos.domain.model.MiCatalogoResult
import com.example.bspos.domain.model.ProductComboComponent
import com.example.bspos.domain.repository.MiCatalogoCatalogRepository
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import java.time.Instant
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MiCatalogoCatalogRepositoryImpl @Inject constructor(
    private val api: MiCatalogoApi,
    private val imageDownloader: MiCatalogoImageDownloader,
    private val transactor: AppDatabaseTransactor,
    private val categories: CategoryDao,
    private val units: UnitOfMeasureDao,
    private val products: ProductDao,
    private val inventory: InventoryDao,
    private val outbox: PosSaleOutboxDao,
    private val json: Json,
    private val customerApi: MiCatalogoCustomerApi,
    private val customers: CustomerRouteDao,
    private val paymentQueue: PaymentSyncDao,
    private val operationQueue: OperationOutboxDao
) : MiCatalogoCatalogRepository {
    private val syncMutex = Mutex()

    override suspend fun archiveRemoteProductsExcept(shopIds: Set<String>) {
        val at = Instant.now()
        if (shopIds.isEmpty()) {
            products.archiveAllRemoteProducts(at)
        } else {
            products.archiveRemoteProductsExcept(shopIds.toList(), at)
        }
    }

    override suspend fun syncCatalog(shopId: String): MiCatalogoResult<MiCatalogoCatalogSyncResult> = syncMutex.withLock { runCatching {
        require(shopId.isNotBlank()) { "La tienda remota no es valida." }
        val sequenceAtDownload = operationQueue.nextSequence() - 1
        // An acknowledgement can arrive while the HTTP snapshot is in flight.
        // Protect events active when the download began as well as those active at apply.
        val mutationsAtDownload = operationQueue.activeForShop(shopId).flatMap { it.productIds.split(',') }.filter { it.isNotBlank() }.toSet()
        val salesAtDownload = pendingRemoteProductIds(shopId)
        val response = api.catalog(shopId)
        if (!response.isSuccessful) error(response.apiErrorMessage("No se pudo descargar el catalogo."))
        val snapshot = checkNotNull(response.body()) { "MiCatalogo devolvio un catalogo vacio." }
        check(snapshot.shop.id == shopId) { "La respuesta no corresponde a la tienda solicitada." }
        val imagePaths = snapshot.products.associate { remote ->
            remote.id to downloadImages(remote)
        }
        val remoteCustomers = try {
            customerApi.customers(shopId).let { response -> if (response.isSuccessful) response.body()?.customers else null }
        } catch (error: Exception) {
            if (error is CancellationException) throw error
            null // Customer availability must not discard a successfully downloaded catalog.
        }
        transactor.runInTransaction {
            val result = applySnapshot(snapshot, imagePaths, salesAtDownload, mutationsAtDownload, sequenceAtDownload)
            if (remoteCustomers != null) applyCustomers(shopId, remoteCustomers)
            result
        }
    }.fold(
        onSuccess = { MiCatalogoResult.Success(it) },
        onFailure = {
            if (it is CancellationException) throw it
            MiCatalogoResult.Failure(it.message ?: "No se pudo sincronizar el catalogo.")
        }
    ) }

    private suspend fun applySnapshot(
        snapshot: CatalogSnapshotDto,
        imagePaths: Map<String, CatalogImagePaths>,
        salesAtDownload: Set<String>,
        mutationsAtDownload: Set<String>,
        sequenceAtDownload: Long
    ): MiCatalogoCatalogSyncResult {
        val shopId = checkNotNull(snapshot.shop.id?.takeIf { it.isNotBlank() }) { "El catalogo no incluye una tienda valida." }
        val mutations = (operationQueue.activeForShop(shopId) + operationQueue.eventsAfter(shopId, sequenceAtDownload))
            .flatMap { it.productIds.split(',') }.filter { it.isNotBlank() }.toSet() + mutationsAtDownload
        val newSaleProducts = outbox.eventsAfter(shopId, sequenceAtDownload).flatMap { row ->
            runCatching { json.decodeFromString<com.example.bspos.data.micatalogo.dto.PosSaleUploadRequestDto>(row.payloadJson) }
                .getOrNull()?.items.orEmpty().map { it.productId }
        }.toSet()
        val pending = pendingRemoteProductIds(shopId) + salesAtDownload + newSaleProducts + mutations
        val pendingSources = snapshot.products.filter { it.id in pending }.map { it.sourceProductId ?: it.id }.toSet()
        val pendingRemoteProductIds = pending + pendingSources + snapshot.products.filter { it.sourceProductId in pendingSources }.map { it.id }
        val synchronizedAt = parseInstant(snapshot.generatedAt, Instant.now())
        val categoryIds = snapshot.categories
            .asSequence()
            .filter { it.id.isNotBlank() }
            .associate { it.id to MiCatalogoImportMapper.categoryId(shopId, it.id) }

        snapshot.categories.distinctBy { it.id }.filter { it.id.isNotBlank() }.forEach { remote ->
            val id = checkNotNull(categoryIds[remote.id])
            val existing = categories.findById(id)
            val timestamp = parseInstant(remote.updatedAt, synchronizedAt)
            val entity = CategoryEntity(
                id = id,
                name = remote.name.trim().ifBlank { "Categoria ${remote.id}" },
                description = null,
                sortOrder = remote.sortOrder,
                isActive = remote.status.trim().lowercase(Locale.ROOT) !in setOf("draft", "suspended", "inactive"),
                createdAt = existing?.createdAt ?: timestamp,
                updatedAt = timestamp,
                deletedAt = null
            )
            if (existing == null) categories.insert(entity) else categories.update(entity)
        }

        val fallbackCategoryId = MiCatalogoImportMapper.fallbackCategoryId(shopId)
        if (snapshot.products.any { it.categoryId !in categoryIds }) {
            val existing = categories.findById(fallbackCategoryId)
            val fallback = CategoryEntity(
                id = fallbackCategoryId,
                name = "Sin categoria (MiCatalogo)",
                description = "Categoria local para productos remotos sin categoria asignada.",
                sortOrder = Int.MAX_VALUE,
                createdAt = existing?.createdAt ?: synchronizedAt,
                updatedAt = synchronizedAt,
                deletedAt = null
            )
            if (existing == null) categories.insert(fallback) else categories.update(fallback)
        }

        val remoteProducts = snapshot.products.distinctBy { it.id }
        val currentRemoteIds = remoteProducts.map { it.id }.toSet()
        // Retired remote products remain in local history but cannot be sold again.
        products.findForShop(shopId).filter { it.miCatalogoProductId !in currentRemoteIds && it.miCatalogoProductId !in pendingRemoteProductIds }.forEach {
            products.update(it.copy(isActive = false, updatedAt = synchronizedAt))
        }
        remoteProducts.map { MiCatalogoImportMapper.normalizedSaleUnit(it.saleUnit) }.distinct().forEach { saleUnit ->
            val id = MiCatalogoImportMapper.unitId(shopId, saleUnit)
            val existing = units.findById(id)
            val entity = UnitOfMeasureEntity(
                id = id,
                name = saleUnit,
                abbreviation = saleUnit.take(12),
                createdAt = existing?.createdAt ?: synchronizedAt,
                updatedAt = synchronizedAt,
                deletedAt = null
            )
            if (existing == null) units.insert(entity) else units.update(entity)
        }

        var inventoryMovementsRecorded = 0
        remoteProducts.filter { it.id.isNotBlank() }.forEach { remote ->
            val productId = products.findRemote(shopId, remote.id)?.id ?: MiCatalogoImportMapper.productId(shopId, remote.id)
            val timestamp = parseInstant(remote.updatedAt, synchronizedAt)
            val existing = products.findRemote(shopId, remote.id)
            // Neither metadata nor a local archive may be reverted by a snapshot
            // taken before an outstanding mutation is acknowledged.
            if (remote.id in mutations && existing != null) return@forEach
            val downloadedImages = imagePaths[remote.id]
            // Keep a resolvable remote fallback when a download fails. This is useful while
            // online, and avoids persisting root-relative paths that Coil cannot load later.
            val remoteImageUrl = imageDownloader.resolve(remote.imageUrl)
            val remoteThumbnailUrl = imageDownloader.resolve(remote.thumbnailUrl ?: remote.imageUrl)
            val imagePath = downloadedImages?.full
                ?: downloadedImages?.thumbnail
                ?: remoteImageUrl
                ?: remoteThumbnailUrl
            val thumbnailPath = downloadedImages?.thumbnail
                ?: downloadedImages?.full
                ?: remoteThumbnailUrl
                ?: remoteImageUrl
            val product = ProductEntity(
                id = productId,
                name = remote.name.trim().ifBlank { "Producto ${remote.id}" },
                internalCode = MiCatalogoImportMapper.internalCode(shopId, remote.id),
                miCatalogoInternalCode = remote.internalCode,
                categoryId = remote.categoryId?.let(categoryIds::get) ?: fallbackCategoryId,
                unitId = MiCatalogoImportMapper.unitId(shopId, remote.saleUnit),
                description = remote.description?.trim()?.ifBlank { null },
                salePrice = MiCatalogoImportMapper.cents(remote.price),
                wholesalePrice = remote.wholesalePrice?.let(MiCatalogoImportMapper::cents),
                miCatalogoShopId = shopId,
                miCatalogoProductId = remote.id,
                miCatalogoProductSlug = remote.slug,
                miCatalogoSourceProductId = remote.sourceProductId,
                miCatalogoVolumeMl = remote.volumeMl,
                miCatalogoAvailableMl = if (remote.id in pendingRemoteProductIds) existing?.miCatalogoAvailableMl else remote.inventory.availableMl,
                miCatalogoReservedDecantMl = if (remote.id in pendingRemoteProductIds) existing?.miCatalogoReservedDecantMl else remote.inventory.reservedDecantMl,
                miCatalogoOpenedBottles = if (remote.id in pendingRemoteProductIds) existing?.miCatalogoOpenedBottles else remote.inventory.openedBottles,
                miCatalogoSaleUnit = remote.saleUnit,
                miCatalogoIsCombo = remote.isCombo,
                miCatalogoComboItemsJson = json.encodeToString(remote.comboItems.map { ProductComboComponent(it.productId, it.quantity) }),
                averageCost = MiCatalogoImportMapper.cents(remote.inventory.costPrice),
                lastPurchaseCost = MiCatalogoImportMapper.cents(remote.inventory.costPrice),
                minimumStock = remote.inventory.lowStockThreshold?.toLong() ?: 0L,
                imagePath = imagePath,
                thumbnailPath = thumbnailPath,
                isActive = MiCatalogoImportMapper.isProductActive(remote.availabilityStatus, remote.moderationStatus),
                createdAt = existing?.createdAt ?: timestamp,
                updatedAt = timestamp,
                deletedAt = null
            )
            if (existing == null) products.insert(product) else products.update(product)
            inventoryMovementsRecorded += applyInventorySnapshot(
                shopId,
                remote,
                productId,
                timestamp,
                remote.id in pendingRemoteProductIds
            )
        }

        return MiCatalogoCatalogSyncResult(
            shopName = snapshot.shop.name?.ifBlank { shopId } ?: shopId,
            categoriesApplied = categoryIds.size + if (snapshot.products.any { it.categoryId !in categoryIds }) 1 else 0,
            unitsApplied = remoteProducts.map { MiCatalogoImportMapper.normalizedSaleUnit(it.saleUnit) }.distinct().size,
            productsApplied = remoteProducts.count { it.id.isNotBlank() },
            inventoryMovementsRecorded = inventoryMovementsRecorded,
            imagesDownloaded = remoteProducts.count { remote ->
                imagePaths[remote.id]?.let { it.full != null || it.thumbnail != null } == true
            },
            imagesFailed = remoteProducts.count { remote ->
                val hasImage = !remote.imageUrl.isNullOrBlank() || !remote.thumbnailUrl.isNullOrBlank()
                val paths = imagePaths[remote.id]
                hasImage && (paths == null || (paths.full == null && paths.thumbnail == null))
            }
        )
    }

    private suspend fun applyCustomers(shopId: String, remoteCustomers: List<RemoteCustomerDto>) {
        // A stale remote balance must never overwrite local pending sales or collections.
        val hasUnsent = outbox.findActiveForShop(shopId).isNotEmpty() || paymentQueue.unsentCount(shopId) > 0
        remoteCustomers.forEach { remote ->
            val clientId = remote.clientCustomerUuid?.let { runCatching { java.util.UUID.fromString(it) }.getOrNull() }
            val existing = customers.findRemoteCustomer(remote.id) ?: clientId?.let { customers.findCustomer(it) }
            val id = existing?.id ?: java.util.UUID.nameUUIDFromBytes("micatalogo:customer:$shopId:${remote.id}".toByteArray(Charsets.UTF_8))
            val at = remote.updatedAt?.let { parseInstant(it, Instant.now()) } ?: Instant.now()
            val base = existing ?: CustomerEntity(id, remote.name ?: "Cliente", createdAt = at, updatedAt = at)
            val entity = base.copy(businessName = remote.name ?: base.businessName, phone = remote.phone,
                whatsapp = remote.whatsapp, address = remote.address, reference = remote.reference,
                firstName = remote.firstName, lastName = remote.lastName, documentType = remote.documentType,
                documentNumber = remote.documentNumber, email = remote.email,
                miCatalogoCustomerId = remote.id, miCatalogoCustomerShopId = shopId,
                creditLimit = MiCatalogoImportMapper.cents(remote.creditLimit),
                balance = if (hasUnsent && existing != null) existing.balance else MiCatalogoImportMapper.cents(remote.balance),
                isActive = remote.isActive ?: true, updatedAt = at)
            if (existing == null) customers.insertCustomer(entity) else customers.updateCustomer(entity)
        }
    }

    private suspend fun applyInventorySnapshot(
        shopId: String,
        remote: RemoteProductDto,
        productId: java.util.UUID,
        timestamp: Instant,
        hasPendingSale: Boolean
    ): Int {
        // A remote count predates every locally pending sale. Applying it would undo those sales.
        if (!remote.inventory.trackInventory || hasPendingSale) return 0
        val target = (remote.inventory.stockQuantity ?: remote.inventory.availableMl)?.toLong() ?: return 0
        require(target >= 0) { "El inventario remoto de ${remote.name} no puede ser negativo." }

        val location = InventoryLocation.MAIN
        val current = inventory.findStock(productId, location.type, location.id)
        val previous = current?.quantity ?: 0L
        val delta = target - previous
        if (delta == 0L) return 0
        val movementTimestamp = current?.updatedAt?.takeIf { it.isAfter(timestamp) } ?: timestamp
        val type = when {
            current == null -> InventoryMovementType.INITIAL
            delta > 0 -> InventoryMovementType.PHYSICAL_COUNT_IN
            else -> InventoryMovementType.PHYSICAL_COUNT_OUT
        }
        val unitCost = MiCatalogoImportMapper.cents(remote.inventory.costPrice)
        inventory.recordMovements(
            listOf(
                InventoryMovementEntity(
                    id = MiCatalogoImportMapper.inventoryMovementId(shopId, remote.id, remote.updatedAt, target),
                    productId = productId,
                    locationType = InventoryLocationType.MAIN_WAREHOUSE,
                    locationId = location.id,
                    movementType = type,
                    quantity = delta,
                    previousQuantity = previous,
                    newQuantity = target,
                    unitCost = unitCost,
                    totalCost = Math.multiplyExact(delta, unitCost),
                    notes = "Imported from MiCatalogo catalog snapshot",
                    createdAt = movementTimestamp,
                    createdBy = "micatalogo-import"
                )
            ),
            allowNegativeStock = false
        )
        return 1
    }

    private fun parseInstant(value: String, fallback: Instant): Instant = runCatching { Instant.parse(value) }.getOrDefault(fallback)

    private suspend fun pendingRemoteProductIds(shopId: String): Set<String> =
        outbox.findActiveForShop(shopId)
            .flatMap { record ->
                runCatching { json.decodeFromString<com.example.bspos.data.micatalogo.dto.PosSaleUploadRequestDto>(record.payloadJson) }
                    .getOrNull()
                    ?.items
                    .orEmpty()
                    .map { it.productId }
            }
            .toSet()

    private suspend fun downloadImages(remote: RemoteProductDto): CatalogImagePaths =
        CatalogImagePaths(
            full = imageDownloader.download(remote.imageUrl),
            thumbnail = imageDownloader.download(remote.thumbnailUrl ?: remote.imageUrl)
        )

    private data class CatalogImagePaths(
        val full: String?,
        val thumbnail: String?
    )
}
