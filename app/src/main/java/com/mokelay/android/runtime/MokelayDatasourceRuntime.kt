package com.mokelay.android.runtime

import com.mokelay.android.api.MokelayPageApi
import com.mokelay.android.dsl.arrayOrNull
import com.mokelay.android.dsl.displayString
import com.mokelay.android.dsl.objectOrNull
import com.mokelay.android.dsl.stringOrNull
import com.mokelay.android.dsl.valueAtPath
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import okhttp3.HttpUrl.Companion.toHttpUrl

data class MokelayDatasourceKeyValue(
    val key: String,
    val value: JsonElement,
)

data class MokelayDatasourceBodyItem(
    val key: String,
    val dataType: String?,
    val value: JsonElement,
)

data class MokelayDatasourceSchemaSelection(
    val path: String,
    val type: String?,
    val label: String?,
)

data class MokelayDatasourceMatchingField(
    val label: String?,
    val variable: String,
    val matchFieldPath: String,
)

data class MokelayDatasource(
    val type: String,
    val domain: String,
    val path: String,
    val method: String,
    val headerData: List<MokelayDatasourceKeyValue>,
    val bodyData: List<MokelayDatasourceBodyItem>,
    val queryData: List<MokelayDatasourceKeyValue>,
    val schemaSelections: List<MokelayDatasourceSchemaSelection>,
    val matchingExternalFields: List<MokelayDatasourceMatchingField>,
) {
    companion object {
        fun fromJson(value: JsonElement?): MokelayDatasource? {
            val obj = value as? JsonObject ?: return null
            return MokelayDatasource(
                type = obj["type"].stringOrNull().orEmpty(),
                domain = obj["domain"].stringOrNull().orEmpty(),
                path = obj["path"].stringOrNull().orEmpty(),
                method = obj["method"].stringOrNull()?.uppercase().orEmpty().ifEmpty { "GET" },
                headerData = keyValues(obj["headerData"]),
                bodyData = bodyItems(obj["bodyData"]),
                queryData = keyValues(obj["queryData"]),
                schemaSelections = schemaSelections(obj["schemaSelections"]),
                matchingExternalFields = matchingFields(obj["matchingExternalFields"]),
            )
        }

        private fun keyValues(value: JsonElement?): List<MokelayDatasourceKeyValue> {
            return value.arrayOrNull()?.mapNotNull { item ->
                val obj = item.objectOrNull() ?: return@mapNotNull null
                val key = obj["key"].stringOrNull() ?: return@mapNotNull null
                MokelayDatasourceKeyValue(key = key, value = obj["value"] ?: JsonPrimitive(""))
            } ?: emptyList()
        }

        private fun bodyItems(value: JsonElement?): List<MokelayDatasourceBodyItem> {
            return value.arrayOrNull()?.mapNotNull { item ->
                val obj = item.objectOrNull() ?: return@mapNotNull null
                val key = obj["key"].stringOrNull() ?: return@mapNotNull null
                MokelayDatasourceBodyItem(
                    key = key,
                    dataType = obj["dataType"].stringOrNull(),
                    value = obj["value"] ?: JsonNull,
                )
            } ?: emptyList()
        }

        private fun schemaSelections(value: JsonElement?): List<MokelayDatasourceSchemaSelection> {
            return value.arrayOrNull()?.mapNotNull { item ->
                val obj = item.objectOrNull() ?: return@mapNotNull null
                val path = obj["path"].stringOrNull() ?: return@mapNotNull null
                MokelayDatasourceSchemaSelection(
                    path = path,
                    type = obj["type"].stringOrNull(),
                    label = obj["label"].stringOrNull(),
                )
            } ?: emptyList()
        }

        private fun matchingFields(value: JsonElement?): List<MokelayDatasourceMatchingField> {
            return value.arrayOrNull()?.mapNotNull { item ->
                val obj = item.objectOrNull() ?: return@mapNotNull null
                val variable = obj["variable"].stringOrNull() ?: return@mapNotNull null
                val path = obj["matchFieldPath"].stringOrNull() ?: return@mapNotNull null
                MokelayDatasourceMatchingField(
                    label = obj["label"].stringOrNull(),
                    variable = variable,
                    matchFieldPath = path,
                )
            } ?: emptyList()
        }
    }
}

data class MokelayDatasourceResult(
    val rawResponse: JsonElement,
    val schemaSelectionData: Map<String, JsonElement>,
    val matchingExternalFieldData: Map<String, JsonElement>,
)

object MokelayDatasourceRuntime {
    suspend fun execute(
        datasource: MokelayDatasource,
        apiClient: MokelayPageApi,
        blocks: Map<String, Map<String, JsonElement>>,
    ): MokelayDatasourceResult {
        val url = requestUrl(datasource, apiClient, blocks)
        val headers = requestHeaders(datasource, blocks)
        val body = requestBody(datasource, blocks)
        val rawResponse = apiClient.sendJsonRequest(
            url = url,
            method = datasource.method,
            headers = headers,
            body = body,
        )
        val schemaData = datasource.schemaSelections.associate { selection ->
            selection.path to (rawResponse.valueAtPath(selection.path) ?: JsonNull)
        }
        val matchingData = datasource.matchingExternalFields.associate { field ->
            field.variable to (rawResponse.valueAtPath(field.matchFieldPath) ?: JsonNull)
        }

        return MokelayDatasourceResult(
            rawResponse = rawResponse,
            schemaSelectionData = schemaData,
            matchingExternalFieldData = matchingData,
        )
    }

    fun requestUrl(
        datasource: MokelayDatasource,
        apiClient: MokelayPageApi,
        blocks: Map<String, Map<String, JsonElement>>,
    ): String {
        val base = if (datasource.domain == "mokelay" || datasource.domain.isBlank()) {
            apiClient.baseUrl
        } else {
            datasource.domain
        }
        val pathIsAbsolute = datasource.path.startsWith("http://") || datasource.path.startsWith("https://")
        val builder = if (pathIsAbsolute) {
            datasource.path.toHttpUrl().newBuilder()
        } else {
            base.toHttpUrl().newBuilder().encodedPath(datasource.path)
        }

        datasource.queryData.forEach { item ->
            val resolved = resolvedDatasourceValue(item.value, blocks)
            builder.addQueryParameter(item.key, resolved.displayString())
        }

        return builder.build().toString()
    }

    fun requestBody(
        datasource: MokelayDatasource,
        blocks: Map<String, Map<String, JsonElement>>,
    ): JsonElement? {
        if (datasource.method != "POST" && datasource.method != "PUT" && datasource.method != "PATCH" && datasource.method != "DELETE") {
            return null
        }

        return JsonObject(
            datasource.bodyData.associate { item ->
                item.key to normalizedBodyValue(item, blocks)
            },
        )
    }

    private fun requestHeaders(
        datasource: MokelayDatasource,
        blocks: Map<String, Map<String, JsonElement>>,
    ): Map<String, String> {
        return datasource.headerData.associate { item ->
            item.key to resolvedDatasourceValue(item.value, blocks).displayString()
        }
    }

    private fun normalizedBodyValue(
        item: MokelayDatasourceBodyItem,
        blocks: Map<String, Map<String, JsonElement>>,
    ): JsonElement {
        val resolved = resolvedDatasourceValue(item.value, blocks)
        return when (item.dataType) {
            "array" -> resolved.arrayOrNull() ?: JsonArray(emptyList())
            "object" -> resolved.objectOrNull() ?: JsonObject(emptyMap())
            "number" -> resolved.displayString().toDoubleOrNull()?.let(::JsonPrimitive) ?: resolved
            "boolean" -> resolved.displayString().toBooleanStrictOrNull()?.let(::JsonPrimitive) ?: resolved
            else -> resolved
        }
    }

    private fun resolvedDatasourceValue(
        value: JsonElement,
        blocks: Map<String, Map<String, JsonElement>>,
    ): JsonElement {
        val objectValue = value.objectOrNull()
        if (objectValue?.containsKey("template") == true) {
            val sourceBlock = objectValue["sourceBlock"]
            val template = objectValue["template"].stringOrNull().orEmpty()
            val context = if (sourceBlock is JsonObject) sourceBlock else JsonObject(emptyMap())
            return MokelayTemplateResolver.resolveTemplate(template, context)
        }

        if (objectValue?.get("mode").stringOrNull() == "variable") {
            return MokelayTemplateResolver.resolveDatasourceVariable(value, blocks)
        }

        return MokelayProcessors.apply(value, MokelayProcessors.processorsFrom(value))
    }
}
