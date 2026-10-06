package com.example.bspos.data.repository
import com.example.bspos.data.local.dao.SaleDao
import com.example.bspos.data.mapper.*
import com.example.bspos.domain.model.*
import com.example.bspos.domain.repository.SaleRepository
import kotlinx.coroutines.flow.map
import java.util.UUID
import javax.inject.Inject
class SaleRepositoryImpl @Inject constructor(private val dao: SaleDao) : SaleRepository {
    override suspend fun insert(sale: Sale, items: List<SaleItem>) = dao.insertWithItems(sale.toEntity(), items.map { it.toEntity() })
    override fun observeAll() = dao.observeAll().map { it.map { row -> row.toDomain() } }
    override fun observeItems(saleId: UUID) = dao.observeItems(saleId).map { it.map { row -> row.toDomain() } }
    override fun observeCostTotals() = dao.observeCostTotals().map { rows -> rows.associate { it.saleId to it.cost } }
}
