package com.example.bspos.presentation.customer

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import java.util.Locale

object DebtReminderHelper {

    fun buildReminderMessage(
        customerName: String,
        balanceCents: Long,
        shopName: String? = null,
        currencySymbol: String = "RD$"
    ): String {
        val formattedAmount = String.format(Locale.US, "%,.2f", balanceCents / 100.0)
        val storeGreeting = if (!shopName.isNullOrBlank()) " de $shopName" else ""
        return "Hola $customerName, le saludamos$storeGreeting. Le recordamos que presenta un balance pendiente de $currencySymbol $formattedAmount. Agradecemos su gestión de pago. ¡Muchas gracias!"
    }

    fun cleanPhoneNumber(phone: String?): String? {
        if (phone.isNullOrBlank()) return null
        val digits = phone.filter { it.isDigit() }
        if (digits.isEmpty()) return null
        return if (digits.length == 10) {
            "1$digits"
        } else {
            digits
        }
    }

    fun sendWhatsApp(context: Context, phone: String?, message: String) {
        val cleanPhone = cleanPhoneNumber(phone)
        val encodedMessage = Uri.encode(message)
        val uriString = if (!cleanPhone.isNullOrBlank()) {
            "https://wa.me/$cleanPhone?text=$encodedMessage"
        } else {
            "https://wa.me/?text=$encodedMessage"
        }

        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(uriString)).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        try {
            context.startActivity(intent)
        } catch (_: Exception) {
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, message)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            try {
                context.startActivity(Intent.createChooser(shareIntent, "Enviar recordatorio").apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                })
            } catch (_: Exception) {
                Toast.makeText(context, "No se encontró aplicación para enviar el mensaje", Toast.LENGTH_SHORT).show()
            }
        }
    }

    fun copyToClipboard(context: Context, message: String) {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
        if (clipboard != null) {
            val clip = ClipData.newPlainText("Recordatorio de cobro", message)
            clipboard.setPrimaryClip(clip)
            Toast.makeText(context, "Mensaje copiado al portapapeles", Toast.LENGTH_SHORT).show()
        }
    }
}
