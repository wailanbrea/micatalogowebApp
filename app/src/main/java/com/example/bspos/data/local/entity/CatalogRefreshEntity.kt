package com.example.bspos.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/** Durable post-acknowledgement read. Revision prevents clearing a newer request. */
@Entity(tableName = "catalog_refresh")
data class CatalogRefreshEntity(@PrimaryKey val shopId: String, val revision: String, val error: String? = null)
