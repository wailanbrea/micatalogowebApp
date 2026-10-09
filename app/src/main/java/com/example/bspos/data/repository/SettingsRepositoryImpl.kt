package com.example.bspos.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.example.bspos.domain.model.AppSettings
import com.example.bspos.domain.model.CurrencyUnit
import com.example.bspos.domain.model.InvoiceConfig
import com.example.bspos.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

class SettingsRepositoryImpl @Inject constructor(private val dataStore: DataStore<Preferences>) : SettingsRepository {
    override fun observe(): Flow<AppSettings> = dataStore.data.map { values ->
        AppSettings(
            allowNegativeStock = values[ALLOW_NEGATIVE_STOCK] ?: false,
            automaticBackupsEnabled = values[AUTOMATIC_BACKUPS_ENABLED] ?: false,
            routesEnabled = values[ROUTES_ENABLED] ?: false,
            showSupportOnDashboard = values[SHOW_SUPPORT_ON_DASHBOARD] ?: true,
            currency = CurrencyUnit.entries.firstOrNull { it.code == values[CURRENCY] } ?: CurrencyUnit.DOP,
            invoice = InvoiceConfig(
                businessName = values[INVOICE_BUSINESS_NAME] ?: "MiCatalogo",
                taxId = values[INVOICE_TAX_ID] ?: "",
                phone = values[INVOICE_PHONE] ?: "",
                address = values[INVOICE_ADDRESS] ?: "",
                footer = values[INVOICE_FOOTER] ?: "Gracias por tu compra"
            )
        )
    }

    override suspend fun setAllowNegativeStock(enabled: Boolean) { dataStore.edit { it[ALLOW_NEGATIVE_STOCK] = enabled } }
    override suspend fun setAutomaticBackupsEnabled(enabled: Boolean) { dataStore.edit { it[AUTOMATIC_BACKUPS_ENABLED] = enabled } }
    override suspend fun setRoutesEnabled(enabled: Boolean) { dataStore.edit { it[ROUTES_ENABLED] = enabled } }
    override suspend fun setShowSupportOnDashboard(enabled: Boolean) { dataStore.edit { it[SHOW_SUPPORT_ON_DASHBOARD] = enabled } }
    override suspend fun setCurrency(currency: CurrencyUnit) { dataStore.edit { it[CURRENCY] = currency.code } }
    override suspend fun setInvoiceConfig(config: InvoiceConfig) {
        dataStore.edit {
            it[INVOICE_BUSINESS_NAME] = config.businessName
            it[INVOICE_TAX_ID] = config.taxId
            it[INVOICE_PHONE] = config.phone
            it[INVOICE_ADDRESS] = config.address
            it[INVOICE_FOOTER] = config.footer
        }
    }

    private companion object {
        val ALLOW_NEGATIVE_STOCK = booleanPreferencesKey("allow_negative_stock")
        val AUTOMATIC_BACKUPS_ENABLED = booleanPreferencesKey("automatic_backups_enabled")
        val ROUTES_ENABLED = booleanPreferencesKey("routes_enabled")
        val SHOW_SUPPORT_ON_DASHBOARD = booleanPreferencesKey("show_support_on_dashboard")
        val CURRENCY = stringPreferencesKey("currency")
        val INVOICE_BUSINESS_NAME = stringPreferencesKey("invoice_business_name")
        val INVOICE_TAX_ID = stringPreferencesKey("invoice_tax_id")
        val INVOICE_PHONE = stringPreferencesKey("invoice_phone")
        val INVOICE_ADDRESS = stringPreferencesKey("invoice_address")
        val INVOICE_FOOTER = stringPreferencesKey("invoice_footer")
    }
}
