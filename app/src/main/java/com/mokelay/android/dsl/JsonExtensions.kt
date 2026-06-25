package com.mokelay.android.dsl

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive

fun JsonElement?.stringOrNull(): String? = (this as? JsonPrimitive)?.contentOrNull

fun JsonElement?.numberOrNull(): Double? = (this as? JsonPrimitive)?.doubleOrNull

fun JsonElement?.intOrNull(): Int? = (this as? JsonPrimitive)?.intOrNull

fun JsonElement?.booleanOrNullValue(): Boolean? = (this as? JsonPrimitive)?.booleanOrNull

fun JsonElement?.objectOrNull(): JsonObject? = this as? JsonObject

fun JsonElement?.arrayOrNull(): JsonArray? = this as? JsonArray

fun JsonElement?.displayString(): String {
    return when (this) {
        null, JsonNull -> ""
        is JsonPrimitive -> contentOrNull.orEmpty()
        else -> toString()
    }
}

fun JsonElement?.truthy(): Boolean {
    return when (this) {
        null, JsonNull -> false
        is JsonPrimitive -> {
            booleanOrNull ?: numberOrNull()?.let { it != 0.0 } ?: !contentOrNull.isNullOrEmpty()
        }
        is JsonArray -> isNotEmpty()
        is JsonObject -> isNotEmpty()
    }
}

fun JsonElement.valueAtPath(path: String): JsonElement? {
    val tokens = tokenizePath(path)
    var current: JsonElement? = this

    for (token in tokens) {
        current = when (current) {
            is JsonObject -> current[token]
            is JsonArray -> token.toIntOrNull()?.let { current.getOrNull(it) }
            else -> null
        }

        if (current == null) {
            return null
        }
    }

    return current
}

fun jsonPrimitiveFromString(value: String): JsonPrimitive {
    val booleanValue = when (value.lowercase()) {
        "true" -> true
        "false" -> false
        else -> null
    }
    if (booleanValue != null) {
        return JsonPrimitive(booleanValue)
    }

    value.toDoubleOrNull()?.let { number ->
        return JsonPrimitive(number)
    }

    return JsonPrimitive(value)
}

private fun tokenizePath(path: String): List<String> {
    val normalized = path.replace("[]", "")
    val tokens = mutableListOf<String>()
    val current = StringBuilder()
    var index = 0

    fun flush() {
        if (current.isNotEmpty()) {
            tokens += current.toString()
            current.clear()
        }
    }

    while (index < normalized.length) {
        when (val char = normalized[index]) {
            '.' -> {
                flush()
                index += 1
            }
            '[' -> {
                flush()
                val end = normalized.indexOf(']', startIndex = index)
                if (end == -1) {
                    return tokens
                }
                val raw = normalized.substring(index + 1, end).trim()
                val token = raw
                    .removeSurrounding("'")
                    .removeSurrounding("\"")
                if (token.isNotEmpty()) {
                    tokens += token
                }
                index = end + 1
            }
            else -> {
                current.append(char)
                index += 1
            }
        }
    }

    flush()
    return tokens
}
