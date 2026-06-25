package com.mokelay.android.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.mokelay.android.dsl.MokelayBlock
import com.mokelay.android.dsl.MokelayPage
import com.mokelay.android.runtime.MokelayBlockRuntimeHandle
import com.mokelay.android.runtime.MokelayRuntime
import com.mokelay.android.ui.blocks.MAdvanceTableBlock
import com.mokelay.android.ui.blocks.MButtonBlock
import com.mokelay.android.ui.blocks.MFormBlock
import com.mokelay.android.ui.blocks.MInputBlock
import com.mokelay.android.ui.blocks.ParagraphBlock
import com.mokelay.android.ui.blocks.UnsupportedBlock
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

data class RenderContext(
    val runtime: MokelayRuntime,
    val scope: kotlinx.coroutines.CoroutineScope,
    val compact: Boolean = false,
) {
    fun compact(): RenderContext = copy(compact = true)
}

@Composable
fun PageRenderer(
    page: MokelayPage,
    runtime: MokelayRuntime,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(20.dp),
) {
    val scope = rememberCoroutineScope()
    val context = RenderContext(runtime = runtime, scope = scope)

    DisposableEffect(page.uuid) {
        runtime.register(
            MokelayBlockRuntimeHandle(
                id = page.uuid,
                type = "MPage",
                getData = {
                    mapOf(
                        "uuid" to JsonPrimitive(page.uuid),
                        "name" to JsonPrimitive(page.name),
                    )
                },
                callMethod = { method, _ ->
                    when (method) {
                        "close" -> {
                            runtime.closeDialog(buildJsonObject { put("result", true) })
                            buildJsonObject { put("ok", true) }
                        }
                        "getData" -> buildJsonObject {
                            put("uuid", page.uuid)
                            put("name", page.name)
                        }
                        else -> null
                    }
                },
            ),
        )
        onDispose {
            runtime.unregister(page.uuid)
        }
    }

    Column(
        modifier = modifier
            .verticalScroll(rememberScrollState())
            .padding(contentPadding),
    ) {
        page.blocks.forEachIndexed { index, block ->
            RenderBlock(block = block, context = context)
            if (index != page.blocks.lastIndex) {
                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }
}

@Composable
fun RenderBlock(block: MokelayBlock, context: RenderContext) {
    Column(modifier = if (context.compact) Modifier else Modifier.fillMaxWidth()) {
        when (block.type) {
            "paragraph" -> ParagraphBlock(block = block, context = context)
            "MButton" -> MButtonBlock(block = block, context = context)
            "MInput" -> MInputBlock(block = block, context = context)
            "MForm" -> MFormBlock(block = block, context = context)
            "MAdvanceTable" -> MAdvanceTableBlock(block = block, context = context)
            else -> UnsupportedBlock(block = block)
        }
    }
}
