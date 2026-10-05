package com.example.bspos.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.example.bspos.data.local.entity.InventoryAdjustmentReasonEntity
import com.example.bspos.data.local.entity.InventoryMovementEntity
import com.example.bspos.data.local.entity.InventoryStockEntity
import com.example.bspos.domain.model.AdjustmentDirection
import com.example.bspos.domain.model.InventoryLocation
import com.example.bspos.domain.model.InventoryLocationType
import com.example.bspos.domain.model.InventoryMovementType
import kotlinx.coroutines.flow.Flow
import java.time.Instant
import java.util.UUID

@Dao
abstract class InventoryDao {
    @Query("SELECT * FROM inventory_stock WHERE location_type = :type AND location_id = :locationId ORDER BY product_id")
    abstract fun observeStock(type: InventoryLocationType, locationId: String): Flow<List<InventoryStockEntity>>

    @Query("SELECT * FROM inventory_stock WHERE product_id = :productId AND location_type = :type AND location_id = :locationId")
    abstract suspend fun findStock(productId: UUID, type: InventoryLocationType, locationId: String): InventoryStockEntity?

    @Query("""
        SELECT * FROM inventory_movements
        WHERE product_id = :productId AND location_type = :type AND location_id = :locationId
          AND (:fromInclusive IS NULL OR created_at >= :fromInclusive)
          AND (:toExclusive IS NULL OR created_at < :toExclusive)
        ORDER BY created_at DESC, rowid DESC
    """)
    abstract fun observeMovements(
        productId: UUID, type: InventoryLocationType, locationId: String,
        fromInclusive: Instant?, toExclusive: Instant?
    ): Flow<List<InventoryMovementEntity>>

    @Query("SELECT * FROM inventory_movements WHERE id = :id")
    abstract suspend fun findMovement(id: UUID): InventoryMovementEntity?

    @Query("SELECT * FROM inventory_adjustment_reasons WHERE id = :id")
    protected abstract suspend fun findReason(id: UUID): InventoryAdjustmentReasonEntity?

    // Stock mutations are only reachable from the audited transaction below.
    @Insert
    protected abstract suspend fun insertStock(stock: InventoryStockEntity)

    @Update
    protected abstract suspend fun updateStock(stock: InventoryStockEntity): Int

    @Insert
    protected abstract suspend fun insertMovement(movement: InventoryMovementEntity)

    /**
     * Commits all stock changes and their ledger rows, or none of them.
     * previousQuantity acts as a concurrency precondition: stale callers must reload and retry.
     * Financial documents and CPP calculations must join this transaction via the transactor.
     */
    @Transaction
    open suspend fun recordMovements(movements: List<InventoryMovementEntity>, allowNegativeStock: Boolean) {
        require(movements.isNotEmpty()) { "At least one movement is required" }
        require(movements.map { it.id }.distinct().size == movements.size) { "Duplicate movement IDs" }
        for (movement in movements) {
            validateMovement(movement)
            val stock = findStock(movement.productId, movement.locationType, movement.locationId)
            check((stock?.quantity ?: 0L) == movement.previousQuantity) { "Stock changed; reload before retrying" }
            check(stock == null || !movement.createdAt.isBefore(stock.updatedAt)) { "Movement predates current stock" }
            if (movement.movementType == InventoryMovementType.INITIAL) {
                check(stock == null) { "Initial inventory has already been recorded for this location" }
            }
            check(allowNegativeStock || movement.newQuantity >= 0) { "Insufficient stock" }
            movement.reasonId?.let { id ->
                val reason = checkNotNull(findReason(id)) { "Adjustment reason does not exist" }
                check(reason.isActive && reason.deletedAt == null) { "Adjustment reason is not active" }
                require(matchesDirection(reason.direction, movement.quantity)) { "Adjustment reason has incompatible direction" }
            }

            if (stock == null) {
                insertStock(InventoryStockEntity(
                    id = UUID.randomUUID(), productId = movement.productId,
                    locationType = movement.locationType, locationId = movement.locationId,
                    quantity = movement.newQuantity, updatedAt = movement.createdAt
                ))
            } else {
                check(updateStock(stock.copy(quantity = movement.newQuantity, updatedAt = movement.createdAt)) == 1)
            }
            // A constraint failure here also rolls back the preceding stock write.
            insertMovement(movement)
        }
    }

    private fun validateMovement(movement: InventoryMovementEntity) {
        InventoryLocation(movement.locationType, movement.locationId)
        require(movement.quantity != 0L) { "A movement cannot have zero quantity" }
        require(movement.unitCost >= 0L) { "Unit cost cannot be negative" }
        require(Math.addExact(movement.previousQuantity, movement.quantity) == movement.newQuantity) { "Incorrect resulting quantity" }
        require(Math.multiplyExact(movement.quantity, movement.unitCost) == movement.totalCost) { "Incorrect total cost" }
        require(matchesDirection(movement.movementType.direction, movement.quantity)) { "Movement sign does not match its type" }
        require((movement.referenceType == null) == (movement.referenceId == null)) { "Document reference must include both type and ID" }
        if (movement.movementType.requiresReason) {
            require(movement.reasonId != null && !movement.notes.isNullOrBlank()) { "An adjustment requires a reason and notes" }
        }
    }

    private fun matchesDirection(direction: AdjustmentDirection, quantity: Long): Boolean = when (direction) {
        AdjustmentDirection.IN -> quantity > 0
        AdjustmentDirection.OUT -> quantity < 0
        AdjustmentDirection.BOTH -> quantity != 0L
    }
}
