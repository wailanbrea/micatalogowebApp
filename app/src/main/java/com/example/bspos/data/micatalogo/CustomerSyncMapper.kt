package com.example.bspos.data.micatalogo

import com.example.bspos.data.local.entity.CustomerEntity
import com.example.bspos.data.micatalogo.dto.CustomerUploadRequestDto
import java.util.Locale

/** Same immutable client UUID for an online create and every offline retry. */
internal fun CustomerEntity.toRemoteRequest(): CustomerUploadRequestDto {
    val displayName = businessName.trim().ifBlank { "Cliente" }
    val structured = !firstName.isNullOrBlank() && !lastName.isNullOrBlank() &&
        !documentNumber.isNullOrBlank() && !phone.isNullOrBlank() && !address.isNullOrBlank() &&
        documentType?.lowercase(Locale.ROOT) in setOf("cedula", "pasaporte")
    return CustomerUploadRequestDto(
        clientCustomerUuid = id.toString(), firstName = firstName?.trim().orEmpty().ifBlank { displayName },
        lastName = lastName?.trim().orEmpty(),
        documentType = documentType?.lowercase(Locale.ROOT)?.takeIf { it in setOf("cedula", "pasaporte") } ?: "cedula",
        documentNumber = documentNumber.orEmpty(), name = if (structured) null else displayName,
        phone = phone.orEmpty(), email = email, address = address.orEmpty(), whatsapp = whatsapp,
        reference = reference, creditLimit = MiCatalogoPosSaleOutboxMapper.decimalPrice(creditLimit), notes = notes
    )
}
