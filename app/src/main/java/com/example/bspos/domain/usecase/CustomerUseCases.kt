package com.example.bspos.domain.usecase

import com.example.bspos.domain.model.Customer
import com.example.bspos.domain.repository.CustomerRepository
import kotlinx.coroutines.flow.Flow
import java.time.Instant
import java.util.UUID
import javax.inject.Inject

data class CustomerInput(
    val firstName: String,
    val lastName: String,
    val documentType: String,
    val documentNumber: String,
    val phone: String,
    val address: String,
    val creditLimit: Long,
    val email: String? = null,
    val whatsapp: String? = null,
    val reference: String? = null,
    val notes: String? = null
)

class CustomerUseCases @Inject constructor(private val repository: CustomerRepository) {
    fun observe(): Flow<List<Customer>> = repository.observeAll()

    suspend fun create(input: CustomerInput): Customer {
        val fields = input.normalized()
        val now = Instant.now()
        val customer = Customer(
            id = UUID.randomUUID(),
            businessName = fields.fullName,
            firstName = fields.firstName,
            lastName = fields.lastName,
            documentType = fields.documentType,
            documentNumber = fields.documentNumber,
            phone = fields.phone,
            whatsapp = fields.whatsapp,
            address = fields.address,
            reference = fields.reference,
            email = fields.email,
            creditLimit = fields.creditLimit,
            createdAt = now,
            updatedAt = now,
            notes = fields.notes
        )
        repository.insert(customer)
        return customer
    }

    suspend fun update(current: Customer, input: CustomerInput): Boolean {
        val fields = input.normalized()
        require(fields.creditLimit >= current.balance) { "El límite no puede ser menor que el saldo pendiente" }
        return repository.update(
            current.copy(
                businessName = fields.fullName,
                firstName = fields.firstName,
                lastName = fields.lastName,
                documentType = fields.documentType,
                documentNumber = fields.documentNumber,
                phone = fields.phone,
                whatsapp = fields.whatsapp,
                address = fields.address,
                reference = fields.reference,
                email = fields.email,
                creditLimit = fields.creditLimit,
                notes = fields.notes,
                updatedAt = Instant.now()
            )
        )
    }

    suspend fun delete(customer: Customer): Boolean {
        require(customer.balance == 0L) { "Customer has outstanding balance" }
        return repository.softDelete(customer.id, Instant.now())
    }

    private data class NormalizedCustomer(
        val firstName: String,
        val lastName: String,
        val documentType: String,
        val documentNumber: String,
        val phone: String,
        val address: String,
        val creditLimit: Long,
        val email: String?,
        val whatsapp: String?,
        val reference: String?,
        val notes: String?
    ) {
        val fullName: String get() = "$firstName $lastName"
    }

    private fun CustomerInput.normalized(): NormalizedCustomer {
        val firstName = firstName.clean("Nombre")
        val lastName = lastName.clean("Apellido")
        val documentType = documentType.trim().lowercase().also {
            require(it == "cedula" || it == "pasaporte") { "Selecciona cédula o pasaporte" }
        }
        val documentNumber = documentNumber.clean("Número de documento")
        val phone = phone.clean("Teléfono")
        val address = address.clean("Dirección")
        require(creditLimit >= 0) { "El límite de crédito no puede ser negativo" }
        return NormalizedCustomer(
            firstName,
            lastName,
            documentType,
            documentNumber,
            phone,
            address,
            creditLimit,
            email.cleanOptional(),
            whatsapp.cleanOptional(),
            reference.cleanOptional(),
            notes.cleanOptional()
        )
    }
}
