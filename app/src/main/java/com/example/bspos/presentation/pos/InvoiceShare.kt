package com.example.bspos.presentation.pos

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri

import com.example.bspos.presentation.customer.DebtReminderHelper

fun shareInvoicePdf(context: Context, pdfUri: Uri, invoiceNumber: String, customerPhone: String? = null) {
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "application/pdf"
        putExtra(Intent.EXTRA_SUBJECT, "Factura $invoiceNumber")
        putExtra(Intent.EXTRA_TEXT, "Adjunto el comprobante $invoiceNumber en PDF.")
        putExtra(Intent.EXTRA_STREAM, pdfUri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    val whatsappPackage = listOf("com.whatsapp", "com.whatsapp.w4b").firstOrNull { packageName ->
        runCatching { context.packageManager.getPackageInfo(packageName, 0) }.isSuccess
    }
    if (whatsappPackage != null) {
        intent.setPackage(whatsappPackage)
        DebtReminderHelper.cleanPhoneNumber(customerPhone)?.let { digits ->
            intent.putExtra("jid", "$digits@s.whatsapp.net")
        }
    }
    try {
        context.startActivity(Intent.createChooser(intent, if (whatsappPackage != null) "Enviar por WhatsApp" else "Compartir comprobante"))
    } catch (_: ActivityNotFoundException) {
        context.startActivity(Intent.createChooser(intent.apply { setPackage(null) }, "Compartir comprobante"))
    }
}

fun openOrSharePdf(context: Context, pdfUri: Uri, title: String) {
    val viewIntent = Intent(Intent.ACTION_VIEW).apply {
        setDataAndType(pdfUri, "application/pdf")
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    try {
        context.startActivity(Intent.createChooser(viewIntent, title))
    } catch (_: ActivityNotFoundException) {
        val sendIntent = Intent(Intent.ACTION_SEND).apply {
            type = "application/pdf"
            putExtra(Intent.EXTRA_SUBJECT, title)
            putExtra(Intent.EXTRA_STREAM, pdfUri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(sendIntent, title))
    }
}
