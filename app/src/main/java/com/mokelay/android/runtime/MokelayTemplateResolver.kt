package com.mokelay.android.runtime

import com.mokelay.android.dsl.MokelayBlock
import com.mokelay.android.dsl.displayString
import com.mokelay.android.dsl.objectOrNull
import com.mokelay.android.dsl.stringOrNull
import com.mokelay.android.dsl.valueAtPath
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject

data class MokelayActionRecord(
    val inputs: Map<String, JsonElement>,
    val outputs: Map<String, JsonElement>,
)

data class MokelayActionState(
    var blocks: Map<String, Map<String, JsonElement>>,
    val sourceBlock: MokelayBlock,
    val actions: MutableMap<String, MokelayActionRecord> = mutableMapOf(),
) {
    fun contextJson(): JsonObject = buildJsonObject {
        put("sourceBlock", sourceBlock.toJsonElement())
        put(
            "blocks",
            JsonObject(
                blocks.mapValues { (_, blockData) ->
                    JsonObject(blockData)
                },
            ),
        )
        put(
            "actions",
            JsonObject(
                actions.mapValues { (_, record) ->
                    buildJsonObject {
                        put("inputs", JsonObject(record.inputs))
                        put("outputs", JsonObject(record.outputs))
                    }
                },
            ),
        )
    }
}

object MokelayTemplateResolver {
    private val templateRegex = Regex("""\{\{\s*([^}]+?)\s*\}\}""")

    fun resolve(value: JsonElement, state: MokelayActionState): JsonElement {
        val objectValue = value.objectOrNull()
        val template = objectValue?.get("template")?.stringOrNull()
        if (template != null) {
            return resolveTemplate(template, state.contextJson())
        }

        return when (value) {
            is JsonObject -> JsonObject(value.mapValues { (_, child) -> resolve(child, state) })
            is JsonArray -> JsonArray(value.map { child -> resolve(child, state) })
            else -> value
        }
    }

    fun interpolate(value: JsonElement, row: JsonObject): JsonElement {
        return when (val template = value.stringOrNull()) {
            null -> {
                val objectValue = value.objectOrNull() ?: return value
                JsonObject(objectValue.mapValues { (_, child) -> interpolate(child, row) })
            }
            else -> resolveTemplate(template, row)
        }
    }

    fun resolveTemplate(template: String, context: JsonObject): JsonElement {
        val matches = templateRegex.findAll(template).toList()
        if (matches.isEmpty()) {
            return JsonPrimitive(template)
        }

        if (matches.size == 1 && matches.first().range.first == 0 && matches.first().range.last == template.lastIndex) {
            return context.valueAtPath(matches.first().groupValues[1].trim())
                ?: JsonPrimitive("")
        }

        var result = template
        matches.forEach { match ->
            val replacement = context.valueAtPath(match.groupValues[1].trim()).displayString()
            result = result.replace(match.value, replacement)
        }
        return JsonPrimitive(result)
    }

    fun resolveDatasourceVariable(
        value: JsonElement,
        blocks: Map<String, Map<String, JsonElement>>,
    ): JsonElement {
        val objectValue = value.objectOrNull() ?: return value
        val mode = objectValue["mode"].stringOrNull()

        val resolved = when (mode) {
            "variable" -> {
                val blockId = objectValue["blockId"].stringOrNull()
                val variable = objectValue["variable"].stringOrNull()
                if (blockId != null && variable != null) {
                    blocks[blockId]?.get(variable) ?: JsonPrimitive("")
                } else {
                    JsonPrimitive("")
                }
            }
            else -> {
                val template = objectValue["template"].stringOrNull()
                if (template != null) {
                    resolveTemplate(template, JsonObject(emptyMap()))
                } else {
                    value
                }
            }
        }

        return MokelayProcessors.apply(resolved, MokelayProcessors.processorsFrom(value))
    }
}
