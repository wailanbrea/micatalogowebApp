package com.example.bspos.data.local.entity

import androidx.room.*
import java.time.Instant
import java.util.UUID

@Entity(tableName="returns",foreignKeys=[ForeignKey(entity=SaleEntity::class,parentColumns=["id"],childColumns=["sale_id"],onUpdate=ForeignKey.CASCADE,onDelete=ForeignKey.RESTRICT)],indices=[Index(value=["return_number"],name="idx_returns_return_number",unique=true),Index(value=["sale_id"],name="idx_returns_sale_id"),Index(value=["customer_id"],name="idx_returns_customer_id"),Index(value=["date"],name="idx_returns_date")])
data class ReturnEntity(@PrimaryKey val id:UUID,@ColumnInfo(name="return_number") val returnNumber:String,@ColumnInfo(name="sale_id") val saleId:UUID,@ColumnInfo(name="customer_id") val customerId:UUID?=null,val date:Instant,@ColumnInfo(name="total_amount") val totalAmount:Long,val reason:String?=null,@ColumnInfo(name="created_at") val createdAt:Instant)

@Entity(tableName="return_items",foreignKeys=[ForeignKey(entity=ReturnEntity::class,parentColumns=["id"],childColumns=["return_id"],onUpdate=ForeignKey.CASCADE,onDelete=ForeignKey.CASCADE),ForeignKey(entity=SaleItemEntity::class,parentColumns=["id"],childColumns=["sale_item_id"],onUpdate=ForeignKey.CASCADE,onDelete=ForeignKey.RESTRICT),ForeignKey(entity=ProductEntity::class,parentColumns=["id"],childColumns=["product_id"],onUpdate=ForeignKey.CASCADE,onDelete=ForeignKey.RESTRICT)],indices=[Index(value=["return_id"],name="idx_return_items_return_id"),Index(value=["sale_item_id"],name="idx_return_items_sale_item_id"),Index(value=["product_id"],name="idx_return_items_product_id"),Index(value=["return_id","sale_item_id"],name="idx_return_items_unique",unique=true)])
data class ReturnItemEntity(@PrimaryKey val id:UUID,@ColumnInfo(name="return_id") val returnId:UUID,@ColumnInfo(name="sale_item_id") val saleItemId:UUID,@ColumnInfo(name="product_id") val productId:UUID,val quantity:Long,@ColumnInfo(name="refund_price") val refundPrice:Long,@ColumnInfo(name="unit_cost_snapshot") val unitCostSnapshot:Long,@ColumnInfo(name="restock_inventory") val restockInventory:Boolean=true,
    @ColumnInfo(name="refund_total_cents", defaultValue="NULL") val refundTotalCents:Long?=null) {
    val refundedCents: Long get() = refundTotalCents ?: Math.multiplyExact(quantity, refundPrice)
}
