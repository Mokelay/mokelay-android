package com.mokelay.android.ui.blocks

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import com.mokelay.android.dsl.MokelayBlock
import com.mokelay.android.dsl.displayString
import com.mokelay.android.runtime.MokelayBlockRuntimeHandle
import com.mokelay.android.ui.RenderContext
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

@Composable
fun MInputBlock(block: MokelayBlock, context: RenderContext) {
    var value by remember(block.id) { mutableStateOf(block.data["value"].displayString()) }
    val focusRequester = remember { FocusRequester() }

    DisposableEffect(block.id) {
        context.runtime.register(
            MokelayBlockRuntimeHandle(
                id = block.id,
                type = block.type,
                getData = {
                    mapOf("value" to JsonPrimitive(value))
                },
                callMethod = { method, _ ->
                    when (method) {
                        "getData" -> buildJsonObject { put("value", value) }
                        "focus" -> {
                            focusRequester.requestFocus()
                            buildJsonObject { put("ok", true) }
                        }
                        else -> null
                    }
                },
            ),
        )
        onDispose {
            context.runtime.unregister(block.id)
        }
    }

    OutlinedTextField(
        value = value,
        onValueChange = { value = it },
        modifier = Modifier
            .fillMaxWidth()
            .focusRequester(focusRequester),
        singleLine = true,
        placeholder = {
            Text(block.data["placeholder"].displayString())
        },
    )
}
