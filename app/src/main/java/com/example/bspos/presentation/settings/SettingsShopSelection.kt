package com.example.bspos.presentation.settings

import com.example.bspos.domain.model.MiCatalogoShop

internal fun resolveSettingsShop(shops: List<MiCatalogoShop>, selectedId: String?, activeId: String?): MiCatalogoShop? =
    shops.firstOrNull { it.id == selectedId }
        ?: shops.firstOrNull { it.id == activeId }
        ?: shops.firstOrNull()
