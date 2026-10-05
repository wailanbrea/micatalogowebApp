package com.example.bspos.presentation.update

import androidx.test.platform.app.InstrumentationRegistry
import com.example.bspos.data.micatalogo.AppUpdateRepository
import com.example.bspos.data.micatalogo.api.MiCatalogoApi
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import androidx.test.ext.junit.runners.AndroidJUnit4

@RunWith(AndroidJUnit4::class)
class PendingAppUpdateTest {
    @Test
    fun pendingUpdateSurvivesRepositoryRecreationAndClearsOnlyAfterInstallation() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val api = Retrofit.Builder().baseUrl("https://example.test/").build().create(MiCatalogoApi::class.java)
        fun repository() = AppUpdateRepository(context, api, OkHttpClient())
        val update = AvailableAppUpdate(99, "test", "https://example.test/app.apk", "a".repeat(64), "", true)

        repository().rememberUpdate(update)
        assertEquals(update, repository().pendingUpdate(98))
        assertEquals(update, repository().pendingUpdate(98))
        assertNull(repository().pendingUpdate(99))
        assertNull(repository().pendingUpdate(98))
    }
}
