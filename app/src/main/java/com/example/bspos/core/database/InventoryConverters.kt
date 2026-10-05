package com.example.bspos.core.database

import androidx.room.TypeConverter
import com.example.bspos.domain.model.AdjustmentDirection
import com.example.bspos.domain.model.InventoryLocationType
import com.example.bspos.domain.model.InventoryMovementType
import com.example.bspos.domain.model.InventoryReferenceType
import com.example.bspos.domain.model.InventoryDocumentStatus
import com.example.bspos.domain.model.InventoryCountStatus
import com.example.bspos.domain.model.RouteLoadStatus
import com.example.bspos.domain.model.SalePaymentType
import com.example.bspos.domain.model.SaleStatus
import com.example.bspos.domain.model.PaymentMethod
import com.example.bspos.domain.model.BackupType
import com.example.bspos.domain.model.BackupStatus
import com.example.bspos.domain.model.CashSessionStatus
import com.example.bspos.domain.model.CashMovementType
import com.example.bspos.domain.model.PosSaleOutboxState

/** Enum names are part of the persisted schema; never rename them without a migration. */
class InventoryConverters {
    @TypeConverter fun locationToString(value: InventoryLocationType): String = value.name
    @TypeConverter fun stringToLocation(value: String): InventoryLocationType = InventoryLocationType.valueOf(value)
    @TypeConverter fun movementToString(value: InventoryMovementType): String = value.name
    @TypeConverter fun stringToMovement(value: String): InventoryMovementType = InventoryMovementType.valueOf(value)
    @TypeConverter fun directionToString(value: AdjustmentDirection): String = value.name
    @TypeConverter fun stringToDirection(value: String): AdjustmentDirection = AdjustmentDirection.valueOf(value)
    @TypeConverter fun referenceToString(value: InventoryReferenceType?): String? = value?.name
    @TypeConverter fun stringToReference(value: String?): InventoryReferenceType? = value?.let(InventoryReferenceType::valueOf)
    @TypeConverter fun documentStatusToString(value: InventoryDocumentStatus): String = value.name
    @TypeConverter fun stringToDocumentStatus(value: String): InventoryDocumentStatus = InventoryDocumentStatus.valueOf(value)
    @TypeConverter fun countStatusToString(value: InventoryCountStatus): String = value.name
    @TypeConverter fun stringToCountStatus(value: String): InventoryCountStatus = InventoryCountStatus.valueOf(value)
    @TypeConverter fun routeLoadStatusToString(value: RouteLoadStatus): String = value.name
    @TypeConverter fun stringToRouteLoadStatus(value: String): RouteLoadStatus = RouteLoadStatus.valueOf(value)
    @TypeConverter fun salePaymentToString(value: SalePaymentType): String = value.name
    @TypeConverter fun stringToSalePayment(value: String): SalePaymentType = SalePaymentType.valueOf(value)
    @TypeConverter fun saleStatusToString(value: SaleStatus): String = value.name
    @TypeConverter fun stringToSaleStatus(value: String): SaleStatus = SaleStatus.valueOf(value)
    @TypeConverter fun paymentMethodToString(value: PaymentMethod): String = value.name
    @TypeConverter fun stringToPaymentMethod(value: String): PaymentMethod = PaymentMethod.valueOf(value)
    @TypeConverter fun backupTypeToString(value: BackupType): String = value.name
    @TypeConverter fun stringToBackupType(value: String): BackupType = BackupType.valueOf(value)
    @TypeConverter fun backupStatusToString(value: BackupStatus): String = value.name
    @TypeConverter fun stringToBackupStatus(value: String): BackupStatus = BackupStatus.valueOf(value)
    @TypeConverter fun cashSessionStatusToString(value: CashSessionStatus): String = value.name
    @TypeConverter fun stringToCashSessionStatus(value: String): CashSessionStatus = CashSessionStatus.valueOf(value)
    @TypeConverter fun cashMovementTypeToString(value: CashMovementType): String = value.name
    @TypeConverter fun stringToCashMovementType(value: String): CashMovementType = CashMovementType.valueOf(value)
    @TypeConverter fun posSaleOutboxStateToString(value: PosSaleOutboxState): String = value.name
    @TypeConverter fun stringToPosSaleOutboxState(value: String): PosSaleOutboxState = PosSaleOutboxState.valueOf(value)
}
