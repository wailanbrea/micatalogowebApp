package com.example.bspos.data.local.tenant

import androidx.test.platform.app.InstrumentationRegistry
import com.example.bspos.BuildConfig
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject

/** A new owner/two stores per live case: no test depends on execution order or wipes prior fixtures. */
object LiveQaFixture {
    fun optional(scenario: String): JSONObject? {
        if (InstrumentationRegistry.getArguments().getString("syncQaLive") != "true") return null
        check(BuildConfig.APPLICATION_ID.contains(".offlinecheck"))
        check(BuildConfig.MICATALOGO_API_BASE_URL == "http://127.0.0.1:18767/")
        val client = OkHttpClient()
        val base = BuildConfig.MICATALOGO_API_BASE_URL
        val identity = client.newCall(Request.Builder().url(base + "__qa/identity").build()).execute().use { response ->
            check(response.isSuccessful); JSONObject(checkNotNull(response.body).string())
        }
        check(identity.getString("environment") == "testing" && identity.getString("driver") == "sqlite")
        check(identity.getString("database") == "C:/Users/waila/AppData/Local/Temp/opencode/micatalogo-sync-20261012.sqlite")
        println("QA_DATABASE_IDENTITY: $identity")
        val fixture = client.newCall(Request.Builder().url(base + "__qa/scenario")
            .post(JSONObject().put("scenario", scenario).toString().toRequestBody("application/json".toMediaType())).build()).execute().use { response ->
            check(response.isSuccessful) { "QA fixture HTTP ${response.code}; inspect the isolated server log." }
            JSONObject(checkNotNull(response.body).string())
        }
        println("QA_FIXTURE: $scenario shop_a=${fixture.getString("shop_a")} shop_b=${fixture.getString("shop_b")} before=${fixture.getJSONObject("before")}")
        client.dispatcher.executorService.shutdown()
        return fixture
    }
}
