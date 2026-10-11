package com.example.bspos.data.repository
import com.example.bspos.data.local.dao.SaleDao
import com.example.bspos.core.database.ShopScope
import com.example.bspos.data.local.entity.TenantMetadata
import com.example.bspos.data.mapper.*
import com.example.bspos.domain.model.*
import com.example.bspos.domain.repository.SaleRepository
import kotlinx.coroutines.flow.map
import java.util.UUID
import javax.inject.Inject
class SaleRepositoryImpl @Inject constructor(private val dao: SaleDao, private val shops: ShopScope) : SaleRepository {
    override suspend fun insert(sale: Sale, items: List<SaleItem>) = shops.mutate { dao.insertWithItems(sale.toEntity().copy(tenant=TenantMetadata.assigned(it)), items.map { it.toEntity() }) }
    override fun observeAll() = shops.observe(dao::observeAll).map { it.map { row -> row.toDomain() } }
    override fun observeItems(saleId: UUID) = shops.observe { dao.observeItems(saleId, it) }.map { it.map { row -> row.toDomain() } }
    override fun observeAllItems() = shops.observe(dao::observeAllItems).map { it.map { row -> row.toDomain() } }
    override fun observeCostTotals() = shops.observe(dao::observeCostTotals).map { rows -> rows.associate { it.saleId to it.cost } }
}
