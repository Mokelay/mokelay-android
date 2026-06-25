package com.mokelay.android.dsl

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

@Serializable
data class MokelayPage(
    val uuid: String,
    val name: String = "",
    val blocks: List<MokelayBlock> = emptyList(),
)

@Serializable
data class MokelayBlock(
    val id: String,
    val type: String,
    val data: JsonObject = JsonObject(emptyMap()),
    val events: List<MokelayBlockEvent> = emptyList(),
) {
    fun toJsonElement(): JsonObject = buildJsonObject {
        put("id", id)
        put("type", type)
        put("data", data)
        put(
            "events",
            buildJsonArray {
                events.forEach { event ->
                    add(
                        buildJsonObject {
                            put("event", event.event)
                            put(
                                "actions",
                                buildJsonArray {
                                    event.actions.forEach { add(it.toJsonElement()) }
                                },
                            )
                        },
                    )
                }
            },
        )
    }

    companion object {
        fun fromJsonElement(value: JsonElement?): MokelayBlock? {
            val obj = value as? JsonObject ?: return null
            val id = obj["id"].stringOrNull() ?: return null
            val type = obj["type"].stringOrNull() ?: return null
            val data = obj["data"] as? JsonObject ?: JsonObject(emptyMap())
            val events = obj["events"].arrayOrNull()
                ?.mapNotNull(MokelayBlockEvent::fromJsonElement)
                ?: emptyList()
            return MokelayBlock(id = id, type = type, data = data, events = events)
        }
    }
}

@Serializable
data class MokelayBlockEvent(
    val event: String = "",
    val actions: List<MokelayActionConfig> = emptyList(),
) {
    companion object {
        fun fromJsonElement(value: JsonElement?): MokelayBlockEvent? {
            val obj = value as? JsonObject ?: return null
            val event = obj["event"].stringOrNull() ?: return null
            val actions = obj["actions"].arrayOrNull()
                ?.mapNotNull(MokelayActionConfig::fromJsonElement)
                ?: emptyList()
            return MokelayBlockEvent(event = event, actions = actions)
        }
    }
}

@Serializable
data class MokelayActionConfig(
    val uuid: String = "",
    val action: String = "",
    val alias: String? = null,
    val type: String? = null,
    val inputs: JsonObject = JsonObject(emptyMap()),
    val outputs: List<String> = emptyList(),
    val nextAction: String? = null,
    val nodes: List<MokelayActionNode> = emptyList(),
) {
    fun toJsonElement(): JsonObject = buildJsonObject {
        put("uuid", uuid)
        put("action", action)
        alias?.let { put("alias", it) }
        type?.let { put("type", it) }
        put("inputs", inputs)
        put("outputs", JsonArray(outputs.map(::JsonPrimitive)))
        nextAction?.let { put("nextAction", it) } ?: put("nextAction", JsonNull)
        put("nodes", JsonArray(nodes.map(MokelayActionNode::toJsonElement)))
    }

    companion object {
        fun fromJsonElement(value: JsonElement?): MokelayActionConfig? {
            val obj = value as? JsonObject ?: return null
            return MokelayActionConfig(
                uuid = obj["uuid"].stringOrNull().orEmpty(),
                action = obj["action"].stringOrNull().orEmpty(),
                alias = obj["alias"].stringOrNull(),
                type = obj["type"].stringOrNull(),
                inputs = obj["inputs"] as? JsonObject ?: JsonObject(emptyMap()),
                outputs = obj["outputs"].arrayOrNull()?.mapNotNull { it.stringOrNull() } ?: emptyList(),
                nextAction = obj["nextAction"].stringOrNull(),
                nodes = obj["nodes"].arrayOrNull()?.mapNotNull(MokelayActionNode::fromJsonElement) ?: emptyList(),
            )
        }
    }
}

@Serializable
data class MokelayActionNode(
    val uuid: String = "",
    val alias: String? = null,
    val value: JsonElement? = null,
    val nextAction: String? = null,
) {
    fun toJsonElement(): JsonObject = buildJsonObject {
        put("uuid", uuid)
        alias?.let { put("alias", it) }
        put("value", value ?: JsonNull)
        nextAction?.let { put("nextAction", it) } ?: put("nextAction", JsonNull)
    }

    companion object {
        fun fromJsonElement(value: JsonElement?): MokelayActionNode? {
            val obj = value as? JsonObject ?: return null
            return MokelayActionNode(
                uuid = obj["uuid"].stringOrNull().orEmpty(),
                alias = obj["alias"].stringOrNull(),
                value = obj["value"],
                nextAction = obj["nextAction"].stringOrNull(),
            )
        }
    }
}
