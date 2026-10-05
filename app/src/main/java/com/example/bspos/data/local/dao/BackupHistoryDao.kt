package com.example.bspos.data.local.dao

import androidx.room.*
import com.example.bspos.data.local.entity.BackupHistoryEntity
import com.example.bspos.domain.model.BackupStatus
import kotlinx.coroutines.flow.Flow

@Dao abstract class BackupHistoryDao {
 @Query("SELECT * FROM backup_history ORDER BY created_at DESC,id DESC") abstract fun observeAll():Flow<List<BackupHistoryEntity>>
 @Query("SELECT * FROM backup_history WHERE status=:status ORDER BY created_at DESC,id DESC") abstract fun observeByStatus(status:BackupStatus):Flow<List<BackupHistoryEntity>>
 @Insert protected abstract suspend fun insert(value:BackupHistoryEntity)
 @Transaction open suspend fun record(value:BackupHistoryEntity){require(value.fileName.isNotBlank()&&value.filePath.isNotBlank()&&value.sizeBytes>=0);require(value.sha256Hash==null||value.sha256Hash.matches(Regex("[0-9a-fA-F]{64}")));insert(value)}
}
