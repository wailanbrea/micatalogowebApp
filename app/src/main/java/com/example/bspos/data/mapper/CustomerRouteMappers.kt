package com.example.bspos.data.mapper

import com.example.bspos.data.local.entity.CustomerEntity
import com.example.bspos.data.local.entity.RouteCustomerEntity
import com.example.bspos.data.local.entity.RouteEntity
import com.example.bspos.domain.model.CommercialRoute
import com.example.bspos.domain.model.Customer
import com.example.bspos.domain.model.RouteCustomer

fun CustomerEntity.toDomain() = Customer(
    id = id,
    businessName = businessName,
    firstName = firstName,
    lastName = lastName,
    documentType = documentType,
    documentNumber = documentNumber,
    ownerName = ownerName,
    phone = phone,
    whatsapp = whatsapp,
    address = address,
    reference = reference,
    taxId = taxId,
    visitDays = visitDays,
    email = email,
    creditLimit = creditLimit,
    balance = balance,
    latitude = latitude,
    longitude = longitude,
    notes = notes,
    isActive = isActive,
    createdAt = createdAt,
    updatedAt = updatedAt,
    deletedAt = deletedAt
)
fun Customer.toEntity() = CustomerEntity(
    id = id,
    businessName = businessName,
    firstName = firstName,
    lastName = lastName,
    documentType = documentType,
    documentNumber = documentNumber,
    ownerName = ownerName,
    phone = phone,
    whatsapp = whatsapp,
    address = address,
    reference = reference,
    taxId = taxId,
    visitDays = visitDays,
    email = email,
    creditLimit = creditLimit,
    balance = balance,
    latitude = latitude,
    longitude = longitude,
    notes = notes,
    isActive = isActive,
    createdAt = createdAt,
    updatedAt = updatedAt,
    deletedAt = deletedAt
)
fun RouteEntity.toDomain() = CommercialRoute(id, name, code, description, isActive, createdAt, updatedAt)
fun CommercialRoute.toEntity() = RouteEntity(id, name, code, description, isActive, createdAt, updatedAt)
fun RouteCustomerEntity.toDomain() = RouteCustomer(routeId, customerId, visitOrder)
fun RouteCustomer.toEntity() = RouteCustomerEntity(routeId, customerId, visitOrder)
