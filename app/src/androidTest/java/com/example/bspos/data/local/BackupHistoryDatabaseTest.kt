package com.example.bspos.data.local

import android.content.Context
import android.database.sqlite.SQLiteConstraintException
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.bspos.data.local.dao.BackupHistoryDao
import com.example.bspos.data.local.entity.BackupHistoryEntity
import com.example.bspos.domain.model.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.*
import org.junit.runner.RunWith
import java.time.Instant
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class BackupHistoryDatabaseTest {
 private lateinit var db:AppDatabase;private lateinit var history:BackupHistoryDao
 private val at=Instant.parse("2026-09-21T01:00:00Z")
 @Before fun setup(){db=Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(),AppDatabase::class.java).build();history=db.backupHistoryDao()}
 @After fun close()=db.close()
 @Test fun recordPersistsAndFiltersAuditEntries()=runTest{val success=entry(status=BackupStatus.SUCCESS);val failed=entry(status=BackupStatus.FAILED,at=at.plusSeconds(1));history.record(success);history.record(failed);Assert.assertEquals(listOf(failed,success),history.observeAll().first());Assert.assertEquals(listOf(failed),history.observeByStatus(BackupStatus.FAILED).first())}
 @Test fun invalidAuditEntryIsRejectedWithoutWrite()=runTest{fails<IllegalArgumentException>{history.record(entry(fileName="",filePath="",size=-1))};fails<IllegalArgumentException>{history.record(entry(hash="not-a-hash"))};Assert.assertTrue(history.observeAll().first().isEmpty())}
 @Test fun primaryKeyIsNeverReplaced()=runTest{val value=entry();history.record(value);fails<SQLiteConstraintException>{history.record(value.copy(status=BackupStatus.FAILED))};Assert.assertEquals(listOf(value),history.observeAll().first())}
 private fun entry(fileName:String="backup.db",filePath:String="/safe/backup.db",size:Long=12,type:BackupType=BackupType.MANUAL,status:BackupStatus=BackupStatus.SUCCESS,hash:String?="${"a".repeat(64)}",at:Instant=this.at)=BackupHistoryEntity(UUID.randomUUID(),fileName,filePath,size,type,status,hash,at)
 private suspend inline fun <reified T:Throwable> fails(block:suspend()->Unit){try{block()}catch(e:Throwable){if(e is T)return;throw e};throw AssertionError("Expected ${T::class.java.simpleName}")}
}
