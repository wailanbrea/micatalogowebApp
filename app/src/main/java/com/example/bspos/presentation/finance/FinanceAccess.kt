package com.example.bspos.presentation.finance

import com.example.bspos.domain.model.MiCatalogoShop
import com.example.bspos.domain.model.canAccessMiCatalogoMenu

data class FinanceAccess(
    val finance: Boolean = false,
    val expenses: Boolean = false,
    val cash: Boolean = false,
    val dayClose: Boolean = false,
    val mutateExpenses: Boolean = false,
    val mutateCash: Boolean = false,
    val mutateDayClose: Boolean = false
) {
    val tabs: List<Int> get() = buildList {
        if (finance) addAll(listOf(0, 1, 2))
        if (expenses) add(3)
        if (cash) add(4)
    }

    fun limitedTo(keys: Set<String>?): FinanceAccess = if (keys == null) this else copy(
        finance = finance && "finance" in keys,
        expenses = expenses && "expenses" in keys,
        cash = cash && "cash" in keys,
        dayClose = dayClose && "day_close" in keys,
        mutateExpenses = mutateExpenses && "expenses" in keys,
        mutateCash = mutateCash && "cash" in keys,
        mutateDayClose = mutateDayClose && "day_close" in keys
    )

    companion object {
        fun forShop(shop: MiCatalogoShop, actorRole: String): FinanceAccess {
            fun allowed(menu: String): Boolean = menu in shop.menuPermissions && canAccessMiCatalogoMenu(
                false, false, shop.menuPermissions, menu, shop.capabilities, shop.enabledMenuKeys, shop.canManageMenuVisibility
            )
            val readOnly = actorRole == "accountant"
            return FinanceAccess(allowed("finance"), allowed("expenses"), allowed("cash"),
                allowed("day_close"),
                allowed("expenses") && !readOnly, allowed("cash") && !readOnly, allowed("day_close") && !readOnly)
        }
    }
}
