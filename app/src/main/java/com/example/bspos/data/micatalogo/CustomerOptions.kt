package com.example.bspos.data.micatalogo

import com.example.bspos.domain.model.Customer
import com.example.bspos.data.micatalogo.dto.RemoteCustomerDto

fun Customer.asRemoteCustomerOption() = RemoteCustomerDto(
    id = remoteCustomerId ?: id.toString(), clientCustomerUuid = id.toString(), name = fullName,
    firstName = firstName, lastName = lastName, documentType = documentType, documentNumber = documentNumber,
    phone = phone, email = email, address = address, whatsapp = whatsapp, reference = reference,
    creditLimit = MiCatalogoPosSaleOutboxMapper.decimalPrice(creditLimit), balance = MiCatalogoPosSaleOutboxMapper.decimalPrice(balance),
    availableCredit = MiCatalogoPosSaleOutboxMapper.decimalPrice((creditLimit - balance).coerceAtLeast(0L)),
    isActive = isActive, updatedAt = updatedAt.toString(), localOnly = remoteCustomerId == null)
