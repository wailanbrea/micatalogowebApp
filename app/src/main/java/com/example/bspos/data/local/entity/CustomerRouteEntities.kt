package com.example.bspos.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.example.bspos.domain.model.CommercialRoute
import java.time.Instant
import java.util.UUID

@Entity(
    tableName = "customers",
    indices = [
        Index(value = ["balance"], name = "idx_customers_balance"),
        Index(value = ["is_active"], name = "idx_customers_is_active"),
        Index(value = ["micatalogo_customer_id"], name = "idx_customers_micatalogo_customer_id", unique = true)
    ]
)
data class CustomerEntity(
    @PrimaryKey val id: UUID,
    @ColumnInfo(name = "business_name") val businessName: String,
    @ColumnInfo(name = "owner_name") val ownerName: String? = null,
    val phone: String? = null,
    val whatsapp: String? = null,
    val address: String? = null,
    val reference: String? = null,
    @ColumnInfo(name = "tax_id") val taxId: String? = null,
    @ColumnInfo(name = "visit_days") val visitDays: String? = null,
    @ColumnInfo(name = "credit_limit", defaultValue = "0") val creditLimit: Long = 0,
    @ColumnInfo(defaultValue = "0") val balance: Long = 0,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val notes: String? = null,
    @ColumnInfo(name = "is_active", defaultValue = "1") val isActive: Boolean = true,
    @ColumnInfo(name = "created_at") val createdAt: Instant,
    @ColumnInfo(name = "updated_at") val updatedAt: Instant,
    @ColumnInfo(name = "deleted_at") val deletedAt: Instant? = null,
    @ColumnInfo(name = "micatalogo_customer_id") val miCatalogoCustomerId: String? = null,
    @ColumnInfo(name = "first_name") val firstName: String? = null,
    @ColumnInfo(name = "last_name") val lastName: String? = null,
    @ColumnInfo(name = "document_type") val documentType: String? = null,
    @ColumnInfo(name = "document_number") val documentNumber: String? = null,
    val email: String? = null,
    @ColumnInfo(name = "micatalogo_customer_shop_id") val miCatalogoCustomerShopId: String? = null
)

@Entity(tableName = "routes", indices = [Index(value = ["code"], name = "idx_routes_code", unique = true)])
data class RouteEntity(
    @PrimaryKey val id: UUID,
    val name: String,
    val code: String,
    val description: String? = null,
    @ColumnInfo(name = "is_active", defaultValue = "1") val isActive: Boolean = true,
    @ColumnInfo(name = "created_at") val createdAt: Instant,
    @ColumnInfo(name = "updated_at") val updatedAt: Instant
)

@Entity(
    tableName = "route_customers",
    primaryKeys = ["route_id", "customer_id"],
    foreignKeys = [
        ForeignKey(entity = RouteEntity::class, parentColumns = ["id"], childColumns = ["route_id"], onUpdate = ForeignKey.CASCADE, onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = CustomerEntity::class, parentColumns = ["id"], childColumns = ["customer_id"], onUpdate = ForeignKey.CASCADE, onDelete = ForeignKey.CASCADE)
    ],
    indices = [Index(value = ["customer_id"], name = "idx_route_customers_customer_id", unique = true), Index(value = ["route_id", "visit_order"], name = "idx_route_customers_visit_order", unique = true)]
)
data class RouteCustomerEntity(
    @ColumnInfo(name = "route_id") val routeId: UUID,
    @ColumnInfo(name = "customer_id") val customerId: UUID,
    @ColumnInfo(name = "visit_order", defaultValue = "0") val visitOrder: Int = 0
)
