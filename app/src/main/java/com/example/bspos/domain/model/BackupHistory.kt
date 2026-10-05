package com.example.bspos.domain.model

import java.time.Instant
import java.util.UUID

enum class BackupType { AUTO, MANUAL, PRE_RESTORE }
enum class BackupStatus { SUCCESS, FAILED }
data class BackupHistory(val id:UUID,val fileName:String,val filePath:String,val sizeBytes:Long,val type:BackupType,val status:BackupStatus,val sha256Hash:String?=null,val createdAt:Instant)
