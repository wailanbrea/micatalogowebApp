package com.example.bspos.domain.repository

import com.example.bspos.domain.model.AppSettings
import com.example.bspos.domain.model.CurrencyUnit
import com.example.bspos.domain.model.InvoiceConfig
import kotlinx.coroutines.flow.Flow

interface SettingsRepository { fun observe():Flow<AppSettings>;suspend fun setAllowNegativeStock(enabled:Boolean);suspend fun setAutomaticBackupsEnabled(enabled:Boolean);suspend fun setRoutesEnabled(enabled:Boolean);suspend fun setCurrency(currency:CurrencyUnit){};suspend fun setInvoiceConfig(config:InvoiceConfig){} }
