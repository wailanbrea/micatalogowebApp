package com.example.bspos.presentation.printer

import android.bluetooth.BluetoothManager
import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.bspos.data.printer.BluetoothPrinterRepository
import com.example.bspos.data.printer.BluetoothPrinterClient
import com.example.bspos.domain.model.BluetoothPrinter
import com.example.bspos.domain.model.ReceiptPaperWidth
import com.example.bspos.presentation.common.UiErrorBus
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class PairedBluetoothDevice(val name: String, val address: String)

@HiltViewModel
class BluetoothPrinterViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val repository: BluetoothPrinterRepository,
    private val printerClient: BluetoothPrinterClient
) : ViewModel() {
    val printers = repository.observe().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _pairedDevices = MutableStateFlow<List<PairedBluetoothDevice>>(emptyList())
    val pairedDevices = _pairedDevices.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error = _error.asStateFlow()

    fun loadPairedDevices() {
        _error.value = null
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED
            ) {
                error("Concede el permiso de dispositivos cercanos para ver las impresoras vinculadas.")
            }
            val adapter = context.getSystemService(BluetoothManager::class.java)?.adapter
                ?: error("Este dispositivo no tiene Bluetooth")
            adapter.bondedDevices.map { device ->
                PairedBluetoothDevice(device.name ?: "Impresora Bluetooth", device.address)
            }.sortedBy { it.name.lowercase() }
        }.onSuccess { _pairedDevices.value = it }
            .onFailure {
                val message = it.message ?: "No se pudieron leer las impresoras vinculadas"
                _error.value = message
                UiErrorBus.show(message)
            }
    }

    fun add(device: PairedBluetoothDevice) {
        viewModelScope.launch {
            repository.add(BluetoothPrinter(device.name, device.address))
        }
    }

    fun remove(printer: BluetoothPrinter) {
        viewModelScope.launch { repository.remove(printer.address) }
    }

    fun setDefault(printer: BluetoothPrinter) {
        viewModelScope.launch { repository.setDefault(printer.address) }
    }

    fun setPaperWidth(printer: BluetoothPrinter, paperWidth: ReceiptPaperWidth) {
        viewModelScope.launch { repository.setPaperWidth(printer.address, paperWidth) }
    }

    fun testPrint(printer: BluetoothPrinter) {
        viewModelScope.launch {
            runCatching { printerClient.printTest(printer) }
                .onSuccess { UiErrorBus.show("Prueba enviada a ${printer.name}") }
                .onFailure { UiErrorBus.show("No se pudo probar la impresora: ${it.message ?: "verifica Bluetooth"}") }
        }
    }

    fun clearError() {
        _error.value = null
    }
}
