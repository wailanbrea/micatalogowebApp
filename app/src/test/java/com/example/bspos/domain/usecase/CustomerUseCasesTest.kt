package com.example.bspos.domain.usecase

import com.example.bspos.domain.model.Customer
import com.example.bspos.domain.repository.CustomerRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.util.UUID

class CustomerUseCasesTest {
    @Test
    fun cannotDeleteCustomerWithOutstandingBalance() = runTest {
        val customer = Customer(UUID.randomUUID(), "Colmado", balance = 500, createdAt = Instant.now(), updatedAt = Instant.now())
        val repository = FakeCustomers(customer)

        try {
            CustomerUseCases(repository).delete(customer)
        } catch (error: IllegalArgumentException) {
            assertTrue(error.message.orEmpty().contains("outstanding balance"))
            return@runTest
        }
        throw AssertionError("Expected deletion to be rejected")
    }

    private class FakeCustomers(private val value: Customer) : CustomerRepository {
        override fun observeAll(): Flow<List<Customer>> = emptyFlow()
        override suspend fun findById(id: UUID): Customer? = value.takeIf { it.id == id }
        override suspend fun insert(record: Customer) = Unit
        override suspend fun update(record: Customer): Boolean = true
        override suspend fun softDelete(id: UUID, at: Instant): Boolean = true
    }
}
