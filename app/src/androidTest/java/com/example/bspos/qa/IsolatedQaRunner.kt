package com.example.bspos.qa

import android.os.Build
import android.os.Bundle
import androidx.test.runner.AndroidJUnitRunner
import com.example.bspos.BuildConfig

/** Fail closed before any test can open the app or contact a business server. */
class IsolatedQaRunner : AndroidJUnitRunner() {
    override fun onCreate(arguments: Bundle) {
        check(BuildConfig.APPLICATION_ID.contains(".offlinecheck")) { "QA requires an isolated package" }
        check(Build.HARDWARE.contains("ranchu") || Build.HARDWARE.contains("goldfish")) { "QA is emulator-only" }
        val host = java.net.URI(BuildConfig.MICATALOGO_API_BASE_URL).host
        check(host in setOf("127.0.0.1", "localhost", "10.0.2.2")) { "QA requires a local API" }
        val listener = "com.example.bspos.qa.QaResultListener"
        arguments.putString("listener", listOfNotNull(arguments.getString("listener"), listener).joinToString(","))
        super.onCreate(arguments)
    }
}
