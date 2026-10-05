package com.example.bspos.data.repository

import com.example.bspos.domain.repository.CategoryRepository
import com.example.bspos.domain.repository.InventoryRepository
import com.example.bspos.domain.repository.InventoryAdjustmentReasonRepository
import com.example.bspos.domain.repository.InventoryDocumentRepository
import com.example.bspos.domain.repository.ProductRepository
import com.example.bspos.domain.repository.SupplierRepository
import com.example.bspos.domain.repository.UnitOfMeasureRepository
import com.example.bspos.domain.repository.CustomerRepository
import com.example.bspos.domain.repository.RouteRepository
import com.example.bspos.domain.repository.RouteLoadRepository
import com.example.bspos.domain.repository.SaleRepository
import com.example.bspos.domain.repository.SettingsRepository
import com.example.bspos.domain.repository.MiCatalogoConnectionRepository
import com.example.bspos.domain.repository.MiCatalogoCatalogRepository
import com.example.bspos.domain.repository.MiCatalogoPosSaleRepository
import com.example.bspos.domain.repository.FinanceRepository
import com.example.bspos.data.micatalogo.FinanceRepositoryImpl
import com.example.bspos.data.micatalogo.MiCatalogoCatalogRepositoryImpl
import com.example.bspos.data.micatalogo.MiCatalogoConnectionRepositoryImpl
import com.example.bspos.data.micatalogo.MiCatalogoPosSaleRepositoryImpl
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {
    @Binds
    abstract fun bindCategories(repository: CategoryRepositoryImpl): CategoryRepository

    @Binds
    abstract fun bindUnits(repository: UnitOfMeasureRepositoryImpl): UnitOfMeasureRepository

    @Binds
    abstract fun bindProducts(repository: ProductRepositoryImpl): ProductRepository

    @Binds
    abstract fun bindSuppliers(repository: SupplierRepositoryImpl): SupplierRepository

    @Binds
    abstract fun bindInventory(repository: InventoryRepositoryImpl): InventoryRepository

    @Binds
    abstract fun bindInventoryAdjustmentReasons(repository: InventoryAdjustmentReasonRepositoryImpl): InventoryAdjustmentReasonRepository

    @Binds
    abstract fun bindInventoryDocuments(repository: InventoryDocumentRepositoryImpl): InventoryDocumentRepository

    @Binds
    abstract fun bindCustomers(repository: CustomerRepositoryImpl): CustomerRepository

    @Binds
    abstract fun bindRoutes(repository: RouteRepositoryImpl): RouteRepository

    @Binds
    abstract fun bindRouteLoads(repository: RouteLoadRepositoryImpl): RouteLoadRepository
    @Binds abstract fun bindSales(repository: SaleRepositoryImpl): SaleRepository
    @Binds abstract fun bindSettings(repository: SettingsRepositoryImpl): SettingsRepository
    @Binds abstract fun bindMiCatalogoConnection(repository: MiCatalogoConnectionRepositoryImpl): MiCatalogoConnectionRepository
    @Binds abstract fun bindMiCatalogoCatalog(repository: MiCatalogoCatalogRepositoryImpl): MiCatalogoCatalogRepository
    @Binds abstract fun bindMiCatalogoPosSales(repository: MiCatalogoPosSaleRepositoryImpl): MiCatalogoPosSaleRepository
    @Binds abstract fun bindFinance(repository: FinanceRepositoryImpl): FinanceRepository
}
