package com.example.bspos.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.example.bspos.data.local.entity.UnitOfMeasureEntity
import kotlinx.coroutines.flow.Flow
import java.time.Instant
import java.util.UUID

@Dao
interface UnitOfMeasureDao {
    @Query("SELECT * FROM units_of_measure WHERE deleted_at IS NULL ORDER BY name, id")
    fun observeAll(): Flow<List<UnitOfMeasureEntity>>

    @Query("SELECT * FROM units_of_measure WHERE id = :id")
    suspend fun findById(id: UUID): UnitOfMeasureEntity?

    @Insert
    suspend fun insert(unit: UnitOfMeasureEntity)

    @Update
    suspend fun update(unit: UnitOfMeasureEntity): Int

    @Query("UPDATE units_of_measure SET deleted_at = :at, updated_at = :at WHERE id = :id AND deleted_at IS NULL")
    suspend fun softDelete(id: UUID, at: Instant): Int
}
