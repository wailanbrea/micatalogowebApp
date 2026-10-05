package com.example.bspos.data.printer

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.example.bspos.domain.model.ReceiptPaperWidth
import com.example.bspos.domain.model.BluetoothPrinter
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONObject
import javax.inject.Inject

class BluetoothPrinterRepository @Inject constructor(
    private val dataStore: DataStore<Preferences>
) {
    fun observe(): Flow<List<BluetoothPrinter>> = dataStore.data.map { preferences ->
        decode(preferences[PRINTERS] ?: "[]")
    }

    suspend fun add(printer: BluetoothPrinter) {
        dataStore.edit { preferences ->
            val current = decode(preferences[PRINTERS] ?: "[]")
            val alreadyExists = current.any { it.address.equals(printer.address, ignoreCase = true) }
            if (!alreadyExists) {
                val next = current + printer.copy(isDefault = current.none { it.isDefault })
                preferences[PRINTERS] = encode(next)
            }
        }
    }

    suspend fun remove(address: String) {
        dataStore.edit { preferences ->
            val current = decode(preferences[PRINTERS] ?: "[]")
            val removedWasDefault = current.firstOrNull { it.address == address }?.isDefault == true
            val remaining = current.filterNot { it.address == address }
            preferences[PRINTERS] = encode(
                remaining.mapIndexed { index, printer ->
                    printer.copy(isDefault = if (removedWasDefault) index == 0 else printer.isDefault)
                }
            )
        }
    }

    suspend fun setDefault(address: String) {
        dataStore.edit { preferences ->
            val current = decode(preferences[PRINTERS] ?: "[]")
            preferences[PRINTERS] = encode(current.map { it.copy(isDefault = it.address == address) })
        }
    }

    suspend fun setPaperWidth(address: String, paperWidth: ReceiptPaperWidth) {
        dataStore.edit { preferences ->
            val current = decode(preferences[PRINTERS] ?: "[]")
            preferences[PRINTERS] = encode(current.map {
                if (it.address.equals(address, ignoreCase = true)) it.copy(paperWidth = paperWidth) else it
            })
        }
    }

    private fun encode(printers: List<BluetoothPrinter>): String = JSONArray().apply {
        printers.forEach { printer ->
            put(JSONObject().apply {
                put("name", printer.name)
                put("address", printer.address)
                put("default", printer.isDefault)
                put("paperWidth", printer.paperWidth.name)
            })
        }
    }.toString()

    private fun decode(value: String): List<BluetoothPrinter> = runCatching {
        val array = JSONArray(value)
        buildList {
            for (index in 0 until array.length()) {
                val item = array.getJSONObject(index)
                add(
                    BluetoothPrinter(
                        name = item.optString("name", "Impresora Bluetooth"),
                        address = item.getString("address"),
                        isDefault = item.optBoolean("default", false),
                        paperWidth = runCatching { ReceiptPaperWidth.valueOf(item.optString("paperWidth")) }
                            .getOrDefault(ReceiptPaperWidth.MM58)
                    )
                )
            }
        }
    }.getOrDefault(emptyList())

    private companion object {
        val PRINTERS = stringPreferencesKey("bluetooth_printers")
    }
}
