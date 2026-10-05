package com.example.bspos.data.repository

import androidx.datastore.preferences.preferencesDataStoreFile
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.bspos.domain.model.AppSettings
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class SettingsRepositoryTest {
 @Test fun defaultsAreSafeAndUpdatesAreAtomic()=runTest{val context=ApplicationProvider.getApplicationContext<android.content.Context>();val dataStore=PreferenceDataStoreFactory.create{context.preferencesDataStoreFile("settings-test-${UUID.randomUUID()}")};val repository=SettingsRepositoryImpl(dataStore);assertEquals(AppSettings(),repository.observe().first());repository.setAllowNegativeStock(true);assertEquals(AppSettings(allowNegativeStock=true),repository.observe().first());repository.setAutomaticBackupsEnabled(true);assertEquals(AppSettings(allowNegativeStock=true,automaticBackupsEnabled=true),repository.observe().first())}
}
