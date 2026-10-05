package com.example.bspos.data.micatalogo

import com.example.bspos.data.micatalogo.api.MiCatalogoOperationApi
import kotlinx.serialization.json.JsonObject
import retrofit2.Response

abstract class CatalogTestOperationApi : MiCatalogoOperationApi {
    override suspend fun submitFinancial(path: String, payload: JsonObject): Response<JsonObject> =
        error("Catalog tests must not send financial requests")
}
