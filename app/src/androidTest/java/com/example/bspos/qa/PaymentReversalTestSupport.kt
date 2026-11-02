package com.example.bspos.data.micatalogo

import com.example.bspos.core.database.RoomDatabaseTransactor
import com.example.bspos.data.local.AppDatabase

fun testPaymentReversalReconciler(db: AppDatabase) = PaymentReversalReconciler(
    RoomDatabaseTransactor(db), db.paymentSyncDao(), db.paymentDao(), db.saleDao(), db.cashSessionDao(), db.customerRouteDao()
)
