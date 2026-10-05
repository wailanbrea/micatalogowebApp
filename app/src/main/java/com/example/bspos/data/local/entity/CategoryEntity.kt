package com.example.bspos.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.Instant
import java.util.UUID

@Entity(
    tableName = "categories",
    indices = [Index(value = ["is_active"], name = "idx_categories_is_active")]
)
data class CategoryEntity(
    @PrimaryKey val id: UUID,
    val name: String,
    val description: String? = null,
    @ColumnInfo(defaultValue = "'category_default'") val icon: String = "category_default",
    @ColumnInfo(name = "sort_order", defaultValue = "0") val sortOrder: Int = 0,
    @ColumnInfo(name = "is_active", defaultValue = "1") val isActive: Boolean = true,
    @ColumnInfo(name = "created_at") val createdAt: Instant,
    @ColumnInfo(name = "updated_at") val updatedAt: Instant,
    @ColumnInfo(name = "deleted_at") val deletedAt: Instant? = null
)
