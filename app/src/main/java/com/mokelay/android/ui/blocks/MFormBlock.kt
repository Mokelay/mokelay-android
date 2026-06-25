package com.mokelay.android.ui.blocks

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.mokelay.android.dsl.MokelayBlock
import com.mokelay.android.dsl.arrayOrNull
import com.mokelay.android.dsl.objectOrNull
import com.mokelay.android.dsl.stringOrNull
import com.mokelay.android.runtime.MokelayBlockRuntimeHandle
import com.mokelay.android.runtime.MokelayRuntime
import com.mokelay.android.ui.RenderBlock
import com.mokelay.android.ui.RenderContext
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

private data class MFormItem(
    val labelName: String,
    val variableName: String,
    val layout: String,
    val editor: MokelayBlock?,
)

@Composable
fun MFormBlock(block: MokelayBlock, context: RenderContext) {
    val items = block.data["items"].arrayOrNull()?.mapNotNull { item ->
        val obj = item.objectOrNull() ?: return@mapNotNull null
        MFormItem(
            labelName = obj["labelName"].stringOrNull().orEmpty(),
            variableName = obj["variableName"].stringOrNull().orEmpty(),
            layout = obj["layout"].stringOrNull().orEmpty(),
            editor = MokelayBlock.fromJsonElement(obj["editor"]),
        )
    } ?: emptyList()

    DisposableEffect(block.id, items) {
        context.runtime.register(
            MokelayBlockRuntimeHandle(
                id = block.id,
                type = block.type,
                getData = {
                    collectFormData(items, context.runtime, block.id)
                },
                callMethod = { method, _ ->
                    when (method) {
                        "getData" -> JsonObject(collectFormData(items, context.runtime, block.id))
                        else -> null
                    }
                },
            ),
        )
        onDispose {
            context.runtime.unregister(block.id)
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        items.forEach { item ->
            FormItemRow(item = item, context = context)
        }
    }
}

private suspend fun collectFormData(
    items: List<MFormItem>,
    runtime: MokelayRuntime,
    formBlockId: String,
): Map<String, JsonElement> {
    val blockData = runtime.getBlockDataContext(excluding = formBlockId)
    return items.associate { item ->
        item.variableName to (
            item.editor?.let { blockData[it.id]?.get("value") }
                ?: JsonPrimitive("")
            )
    }
}

@Composable
private fun FormItemRow(item: MFormItem, context: RenderContext) {
    if (item.layout == "Horizontal") {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(
                text = item.labelName,
                modifier = Modifier.weight(0.34f),
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
            )
            Column(modifier = Modifier.weight(0.66f)) {
                item.editor?.let { RenderBlock(block = it, context = context) }
            }
        }
    } else {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = item.labelName,
                modifier = Modifier.padding(start = 2.dp),
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
            )
            item.editor?.let { RenderBlock(block = it, context = context) }
        }
    }
}
