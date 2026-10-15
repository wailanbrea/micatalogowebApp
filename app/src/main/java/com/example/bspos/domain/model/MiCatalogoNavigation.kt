package com.example.bspos.domain.model

/** Presentation only; consumers must still check the active shop's effective permissions. */
data class MiCatalogoNavigation(
    val sections: List<MiCatalogoNavigationSection>,
    val secondary: List<MiCatalogoNavigationItem>,
    val contextualSections: List<MiCatalogoNavigationSection>
)

data class MiCatalogoNavigationSection(
    val id: String,
    val label: String,
    val items: List<MiCatalogoNavigationItem>,
    val requiresLocalSetting: String? = null
)

data class MiCatalogoNavigationItem(
    val id: String,
    val key: String,
    val label: String,
    val destination: String
)

/** Keep permission checks at the child, and retain the existing local route setting. */
fun MiCatalogoNavigation.visibleSections(
    canAccess: (String) -> Boolean,
    routesEnabled: Boolean
): List<MiCatalogoNavigationSection> {
    fun allowed(item: MiCatalogoNavigationItem): Boolean =
        item.destination == "dashboard" || item.key in setOf("account", "help") || canAccess(item.key)
    val primary = sections + contextualSections.filter { it.requiresLocalSetting == "routesEnabled" && routesEnabled }
    val result = primary.mapNotNull { section ->
        section.items.filter(::allowed).distinctBy { it.destination }.takeIf { it.isNotEmpty() }
            ?.let { section.copy(items = it) }
    }
    val accountItems = secondary.filter(::allowed)
    return result + if (accountItems.isEmpty()) emptyList() else listOf(
        MiCatalogoNavigationSection("account_help", "Cuenta y ayuda", accountItems)
    )
}
