package com.example.bspos.domain.model

data class MiCatalogoConnectionState(
    val baseUrl: String,
    val hasAccessToken: Boolean,
    val isRemembered: Boolean = false,
    val rememberedEmail: String = "",
    val accountEmail: String = "",
    val accountName: String = "",
    val role: String = "seller",
    /** Store selected by the user when the account has more than one store. */
    val activeShopId: String? = null
) {
    // A build-time endpoint alone is not a tenant connection; a session is required.
    val isConfigured: Boolean get() = hasAccessToken
    val isAdmin: Boolean get() = role.equals("admin", ignoreCase = true)
}
