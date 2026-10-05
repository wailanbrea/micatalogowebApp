package com.example.bspos.domain.repository
import com.example.bspos.domain.model.*
import kotlinx.coroutines.flow.Flow
import java.util.UUID
interface SaleRepository { suspend fun insert(sale:Sale,items:List<SaleItem>);fun observeAll():Flow<List<Sale>>;fun observeItems(saleId:UUID):Flow<List<SaleItem>> }
