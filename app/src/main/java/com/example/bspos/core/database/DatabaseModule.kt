package com.example.bspos.core.database

import android.content.Context
import androidx.room.Room
import com.example.bspos.data.local.AppDatabase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {
    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): AppDatabase =
        Room.databaseBuilder(context, AppDatabase::class.java, AppDatabase.NAME)
            .addMigrations(DatabaseMigrations.MIGRATION_1_2)
            .addMigrations(DatabaseMigrations.MIGRATION_2_3)
            .addMigrations(DatabaseMigrations.MIGRATION_3_4)
            .addMigrations(DatabaseMigrations.MIGRATION_4_5)
            .addMigrations(DatabaseMigrations.MIGRATION_5_6)
            .addMigrations(DatabaseMigrations.MIGRATION_6_7)
            .addMigrations(DatabaseMigrations.MIGRATION_7_8)
            .addMigrations(DatabaseMigrations.MIGRATION_8_9)
            .addMigrations(DatabaseMigrations.MIGRATION_9_10)
            .addMigrations(DatabaseMigrations.MIGRATION_10_11)
            .addMigrations(DatabaseMigrations.MIGRATION_11_12)
            .addMigrations(DatabaseMigrations.MIGRATION_12_13)
            .addMigrations(DatabaseMigrations.MIGRATION_13_14)
            .addMigrations(DatabaseMigrations.MIGRATION_14_15)
            .addMigrations(DatabaseMigrations.MIGRATION_15_16)
            .addMigrations(DatabaseMigrations.MIGRATION_16_17)
            .addMigrations(DatabaseMigrations.MIGRATION_17_18)
            .addMigrations(DatabaseMigrations.MIGRATION_18_19)
            .addMigrations(DatabaseMigrations.MIGRATION_19_20)
            .addCallback(InventoryIntegrity)
            .build()

    @Provides
    fun provideCategoryDao(database: AppDatabase) = database.categoryDao()

    @Provides fun providePaymentSyncDao(database: AppDatabase) = database.paymentSyncDao()
    @Provides fun provideOperationOutboxDao(database: AppDatabase) = database.operationOutboxDao()

    @Provides
    fun provideUnitOfMeasureDao(database: AppDatabase) = database.unitOfMeasureDao()

    @Provides
    fun provideProductDao(database: AppDatabase) = database.productDao()

    @Provides
    fun provideSupplierDao(database: AppDatabase) = database.supplierDao()

    @Provides
    fun provideInventoryDao(database: AppDatabase) = database.inventoryDao()

    @Provides
    fun provideInventoryAdjustmentReasonDao(database: AppDatabase) = database.inventoryAdjustmentReasonDao()

    @Provides
    fun provideInventoryDocumentDao(database: AppDatabase) = database.inventoryDocumentDao()

    @Provides
    fun provideCustomerRouteDao(database: AppDatabase) = database.customerRouteDao()

    @Provides
    fun provideRouteLoadDao(database: AppDatabase) = database.routeLoadDao()
    @Provides fun provideSaleDao(database: AppDatabase) = database.saleDao()
    @Provides fun providePaymentDao(database: AppDatabase) = database.paymentDao()
    @Provides fun provideReturnDao(database: AppDatabase) = database.returnDao()
    @Provides fun provideBackupHistoryDao(database: AppDatabase) = database.backupHistoryDao()
    @Provides fun provideCashSessionDao(database: AppDatabase) = database.cashSessionDao()
    @Provides fun providePosSaleOutboxDao(database: AppDatabase) = database.posSaleOutboxDao()

    @Provides
    @Singleton
    fun provideTransactor(database: AppDatabase): AppDatabaseTransactor = RoomDatabaseTransactor(database)
}
