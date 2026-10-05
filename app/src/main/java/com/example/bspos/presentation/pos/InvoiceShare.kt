package com.example.bspos.presentation.pos

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri

fun shareInvoicePdf(context: Context, pdfUri: Uri, invoiceNumber: String) {
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "application/pdf"
        putExtra(Intent.EXTRA_SUBJECT, "Factura $invoiceNumber")
        putExtra(Intent.EXTRA_TEXT, "Adjunto la factura $invoiceNumber en PDF.")
        putExtra(Intent.EXTRA_STREAM, pdfUri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    val whatsappPackage = listOf("com.whatsapp", "com.whatsapp.w4b").firstOrNull { packageName ->
        runCatching { context.packageManager.getPackageInfo(packageName, 0) }.isSuccess
    }
    if (whatsappPackage != null) {
        intent.setPackage(whatsappPackage)
    }
    try {
        context.startActivity(Intent.createChooser(intent, if (whatsappPackage != null) "Enviar por WhatsApp" else "Compartir factura"))
    } catch (_: ActivityNotFoundException) {
        context.startActivity(Intent.createChooser(intent.apply { setPackage(null) }, "Compartir factura"))
    }
}
