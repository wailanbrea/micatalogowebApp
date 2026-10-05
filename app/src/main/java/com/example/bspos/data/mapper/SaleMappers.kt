package com.example.bspos.data.mapper
import com.example.bspos.data.local.entity.*
import com.example.bspos.domain.model.*
fun SaleEntity.toDomain()=Sale(id,invoiceNumber,customerId,routeId,date,subtotal,discount,tax,total,paymentType,paidAmount,pendingAmount,status,notes,createdAt,updatedAt,saleMode)
fun Sale.toEntity()=SaleEntity(id,invoiceNumber,customerId,routeId,date,subtotal,discount,tax,total,paymentType,paidAmount,pendingAmount,status,notes,createdAt,updatedAt,saleMode)
fun SaleItemEntity.toDomain()=SaleItem(id,saleId,productId,quantity,unitPrice,unitCostSnapshot,discount,tax,subtotal)
fun SaleItem.toEntity()=SaleItemEntity(id,saleId,productId,quantity,unitPrice,unitCostSnapshot,discount,tax,subtotal)
