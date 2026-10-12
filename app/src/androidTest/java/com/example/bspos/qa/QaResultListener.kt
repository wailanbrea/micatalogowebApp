package com.example.bspos.qa

import androidx.test.platform.app.InstrumentationRegistry
import org.junit.runner.Description
import org.junit.runner.Result
import org.junit.runner.notification.Failure
import org.junit.runner.notification.RunListener
import org.json.JSONArray
import org.json.JSONObject

/** PASS never includes an assumption or a test that was not executed. */
class QaResultListener : RunListener() {
    private val outcomes = linkedMapOf<String, String>()
    private fun key(description: Description) = "${description.className}#${description.methodName}"
    override fun testStarted(description: Description) { outcomes[key(description)] = "PASS" }
    override fun testFailure(failure: Failure) { outcomes[key(failure.description)] = "FAIL" }
    override fun testAssumptionFailure(failure: Failure) { outcomes[key(failure.description)] = "ASSUMPTION" }
    override fun testIgnored(description: Description) { outcomes[key(description)] = "SKIP" }
    override fun testRunFinished(result: Result) {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val selection = InstrumentationRegistry.getArguments().getString("class")?.split(',').orEmpty()
        val packages = InstrumentationRegistry.getArguments().getString("package")?.split(',').orEmpty()
        val expected = instrumentation.context.assets.open("qa-test-manifest.txt").bufferedReader().use { it.readLines() }
            .filter { name -> (selection.isEmpty() || selection.any { name == it || name.startsWith("$it#") }) &&
                (packages.isEmpty() || packages.any { name.startsWith("$it.") }) }.toSet()
        val missing = expected - outcomes.keys
        val unexpected = outcomes.keys - expected
        val counts = JSONObject()
        for (state in listOf("PASS", "FAIL", "SKIP", "ASSUMPTION")) counts.put(state, outcomes.values.count { it == state })
        val report = JSONObject().put("declared", expected.size).put("executed", result.runCount).put("counts", counts)
            .put("missing", JSONArray(missing.toList())).put("unexpected", JSONArray(unexpected.toList()))
            .put("gate", if (missing.isEmpty() && unexpected.isEmpty() && outcomes.values.none { it == "FAIL" || it == "ASSUMPTION" || it == "SKIP" }) "PASS" else "NOT_APPROVED")
            .put("tests", JSONArray(outcomes.map { (name, state) -> JSONObject().put("test", name).put("state", state) }))
        java.io.File(instrumentation.targetContext.getExternalFilesDir(null), "qa-results.json").writeText(report.toString(2))
        instrumentation.sendStatus(0, android.os.Bundle().apply { putString("stream", "\nQA_SUMMARY $report\n") })
    }
}
