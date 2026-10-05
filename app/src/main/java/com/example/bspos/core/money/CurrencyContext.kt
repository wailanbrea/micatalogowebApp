package com.example.bspos.core.money

import androidx.compose.runtime.compositionLocalOf
import com.example.bspos.domain.model.CurrencyUnit

val LocalCurrency = compositionLocalOf { CurrencyUnit.DOP }
