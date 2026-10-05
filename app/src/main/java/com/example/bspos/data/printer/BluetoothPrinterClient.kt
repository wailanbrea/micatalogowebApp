package com.example.bspos.data.printer

import android.bluetooth.BluetoothAdapter
import android.annotation.SuppressLint
import android.util.Log
import com.example.bspos.domain.model.BluetoothPrinter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.util.UUID
import javax.inject.Inject

class BluetoothPrinterClient @Inject constructor() {
    @SuppressLint("MissingPermission")
    suspend fun print(printer: BluetoothPrinter, content: String) = withContext(Dispatchers.IO) {
        val adapter = BluetoothAdapter.getDefaultAdapter()
            ?: error("Este dispositivo no tiene Bluetooth")
        val device = adapter.getRemoteDevice(printer.address)
        val socket = device.createRfcommSocketToServiceRecord(SPP_UUID)
        try {
            adapter.cancelDiscovery()
            Log.i(TAG, "Conectando con la impresora ${printer.name}")
            socket.connect()
            socket.outputStream.use { output ->
                val payload = buildEscPosPayload(content)
                output.write(payload)
                output.flush()
                Log.i(TAG, "Ticket enviado a ${printer.name} (${payload.size} bytes)")

                // Algunas impresoras térmicas portátiles pierden el final del trabajo si el
                // socket RFCOMM se cierra inmediatamente después de flush().
                delay(SOCKET_DRAIN_DELAY_MS)
            }
        } catch (error: Throwable) {
            Log.e(TAG, "No se pudo imprimir en ${printer.name}", error)
            throw error
        } finally {
            runCatching { socket.close() }
                .onFailure { Log.w(TAG, "No se pudo cerrar el socket de ${printer.name}", it) }
        }
    }

    @SuppressLint("MissingPermission")
    suspend fun printTest(printer: BluetoothPrinter) {
        print(
            printer,
            listOf(
                "PRUEBA DE IMPRESION",
                printer.name,
                printer.paperWidth.label,
                "Bluetooth OK"
            ).joinToString("\n")
        )
    }
}

/**
 * Crea un trabajo de texto ESC/POS compatible con impresoras térmicas de 58 y 80 mm.
 *
 * Se evita el corte automático: muchas impresoras portátiles no tienen cortador y la
 * orden GS V no es universal. Los saltos CRLF y el avance final sí son compatibles con
 * los modelos ESC/POS más habituales.
 */
internal fun buildEscPosPayload(content: String): ByteArray = ByteArrayOutputStream().use { output ->
    output.write(ESC_POS_INITIALIZE)
    output.write(ESC_POS_ALIGN_LEFT)
    output.write(content.toPrinterLines().toByteArray(PRINTER_CHARSET))
    output.write(TRAILING_FEED.toByteArray(PRINTER_CHARSET))
    output.toByteArray()
}

private fun String.toPrinterLines(): String =
    replace("\r\n", "\n")
        .replace('\r', '\n')
        .trimEnd()
        .replace("\n", "\r\n")

private const val TAG = "BluetoothPrinterClient"
private const val SOCKET_DRAIN_DELAY_MS = 300L
private val SPP_UUID: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
private val ESC_POS_INITIALIZE = byteArrayOf(0x1B, 0x40)
private val ESC_POS_ALIGN_LEFT = byteArrayOf(0x1B, 0x61, 0x00)
private const val TRAILING_FEED = "\r\n\r\n\r\n\r\n\r\n"
private val PRINTER_CHARSET = Charsets.ISO_8859_1
