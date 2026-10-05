package com.example.bspos.data.local.entity

import androidx.room.*
import com.example.bspos.domain.model.BackupStatus
import com.example.bspos.domain.model.BackupType
import java.time.Instant
import java.util.UUID

@Entity(tableName="backup_history",indices=[Index(value=["created_at"],name="idx_backup_history_created_at"),Index(value=["status"],name="idx_backup_history_status")])
data class BackupHistoryEntity(@PrimaryKey val id:UUID,@ColumnInfo(name="file_name") val fileName:String,@ColumnInfo(name="file_path") val filePath:String,@ColumnInfo(name="size_bytes") val sizeBytes:Long,val type:BackupType,val status:BackupStatus,@ColumnInfo(name="sha256_hash") val sha256Hash:String?=null,@ColumnInfo(name="created_at") val createdAt:Instant)
