package com.example.bspos.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.example.bspos.core.database.DatabaseConverters
import com.example.bspos.core.database.InventoryConverters
import com.example.bspos.data.local.dao.InventoryDao
import com.example.bspos.data.local.dao.InventoryAdjustmentReasonDao
import com.example.bspos.data.local.dao.InventoryDocumentDao
import com.example.bspos.data.local.dao.CustomerRouteDao
import com.example.bspos.data.local.dao.RouteLoadDao
import com.example.bspos.data.local.dao.SaleDao
import com.example.bspos.data.local.dao.PaymentDao
import com.example.bspos.data.local.dao.ReturnDao
import com.example.bspos.data.local.dao.BackupHistoryDao
import com.example.bspos.data.local.dao.CashSessionDao
import com.example.bspos.data.local.dao.PosSaleOutboxDao
import com.example.bspos.data.local.entity.InventoryStockEntity
import com.example.bspos.data.local.entity.InventoryMovementEntity
import com.example.bspos.data.local.entity.InventoryAdjustmentReasonEntity
import com.example.bspos.data.local.entity.StockEntryEntity
import com.example.bspos.data.local.entity.StockEntryItemEntity
import com.example.bspos.data.local.entity.PurchaseEntity
import com.example.bspos.data.local.entity.PurchaseItemEntity
import com.example.bspos.data.local.entity.InventoryCountEntity
import com.example.bspos.data.local.entity.InventoryCountItemEntity
import com.example.bspos.data.local.entity.CustomerEntity
import com.example.bspos.data.local.entity.RouteEntity
import com.example.bspos.data.local.entity.RouteCustomerEntity
import com.example.bspos.data.local.entity.RouteLoadEntity
import com.example.bspos.data.local.entity.RouteLoadItemEntity
import com.example.bspos.data.local.entity.SaleEntity
import com.example.bspos.data.local.entity.SaleItemEntity
import com.example.bspos.data.local.entity.PaymentEntity
import com.example.bspos.data.local.entity.PaymentAllocationEntity
import com.example.bspos.data.local.entity.ReturnEntity
import com.example.bspos.data.local.entity.ReturnItemEntity
import com.example.bspos.data.local.entity.BackupHistoryEntity
import com.example.bspos.data.local.entity.CashSessionEntity
import com.example.bspos.data.local.entity.CashMovementEntity
import com.example.bspos.data.local.entity.PosSaleOutboxEntity
import com.example.bspos.data.local.dao.CategoryDao
import com.example.bspos.data.local.dao.ProductDao
import com.example.bspos.data.local.dao.SupplierDao
import com.example.bspos.data.local.dao.UnitOfMeasureDao
import com.example.bspos.data.local.entity.CategoryEntity
import com.example.bspos.data.local.entity.ProductEntity
import com.example.bspos.data.local.entity.SupplierEntity
import com.example.bspos.data.local.entity.UnitOfMeasureEntity

@Database(
    entities = [
        com.example.bspos.data.local.entity.CatalogRefreshEntity::class,
        com.example.bspos.data.local.entity.OperationOutboxEntity::class,
        com.example.bspos.data.local.entity.PaymentSyncEntity::class,
        CategoryEntity::class, UnitOfMeasureEntity::class, ProductEntity::class, SupplierEntity::class,
        InventoryStockEntity::class, InventoryMovementEntity::class, InventoryAdjustmentReasonEntity::class,
        StockEntryEntity::class, StockEntryItemEntity::class, PurchaseEntity::class, PurchaseItemEntity::class,
        InventoryCountEntity::class, InventoryCountItemEntity::class,
        CustomerEntity::class, RouteEntity::class, RouteCustomerEntity::class,
        RouteLoadEntity::class, RouteLoadItemEntity::class, SaleEntity::class, SaleItemEntity::class, PosSaleOutboxEntity::class, PaymentEntity::class, PaymentAllocationEntity::class, ReturnEntity::class, ReturnItemEntity::class, BackupHistoryEntity::class, CashSessionEntity::class, CashMovementEntity::class
    ],
    version = 20,
    exportSchema = true
)
@TypeConverters(DatabaseConverters::class, InventoryConverters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun operationOutboxDao(): com.example.bspos.data.local.dao.OperationOutboxDao
    abstract fun paymentSyncDao(): com.example.bspos.data.local.dao.PaymentSyncDao
    abstract fun categoryDao(): CategoryDao
    abstract fun unitOfMeasureDao(): UnitOfMeasureDao
    abstract fun productDao(): ProductDao
    abstract fun supplierDao(): SupplierDao
    abstract fun inventoryDao(): InventoryDao
    abstract fun inventoryAdjustmentReasonDao(): InventoryAdjustmentReasonDao
    abstract fun inventoryDocumentDao(): InventoryDocumentDao
    abstract fun customerRouteDao(): CustomerRouteDao
    abstract fun routeLoadDao(): RouteLoadDao
    abstract fun saleDao(): SaleDao
    abstract fun paymentDao(): PaymentDao
    abstract fun returnDao(): ReturnDao
    abstract fun backupHistoryDao(): BackupHistoryDao
    abstract fun cashSessionDao(): CashSessionDao
    abstract fun posSaleOutboxDao(): PosSaleOutboxDao

    companion object {
        const val NAME = "micatalogo.db"
    }
}
