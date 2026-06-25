package com.mokelay.android.runtime

import com.mokelay.android.dsl.arrayOrNull
import com.mokelay.android.dsl.objectOrNull
import com.mokelay.android.dsl.stringOrNull
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive

object MokelayProcessors {
    fun apply(value: JsonElement, processors: List<JsonElement>): JsonElement {
        return processors.fold(value) { current, processor ->
            when (processorName(processor)) {
                "trim" -> {
                    val text = current.stringOrNull()
                    if (text == null) current else JsonPrimitive(text.trim())
                }
                else -> current
            }
        }
    }

    fun processorsFrom(value: JsonElement?): List<JsonElement> {
        return value
            ?.objectOrNull()
            ?.get("processors")
            ?.arrayOrNull()
            ?.toList()
            ?: emptyList()
    }

    private fun processorName(value: JsonElement): String? {
        return value.stringOrNull()
            ?: value.objectOrNull()?.get("processor").stringOrNull()
            ?: value.objectOrNull()?.get("name").stringOrNull()
    }
}
