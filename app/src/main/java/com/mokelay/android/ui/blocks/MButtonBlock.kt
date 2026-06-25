package com.mokelay.android.ui.blocks

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.mokelay.android.dsl.MokelayBlock
import com.mokelay.android.dsl.displayString
import com.mokelay.android.ui.RenderContext

@Composable
fun MButtonBlock(block: MokelayBlock, context: RenderContext) {
    val label = block.data["label"].displayString().ifBlank { "按钮" }
    val variant = block.data["variant"].displayString()
    val align = when (block.data["align"].displayString()) {
        "center" -> Alignment.Center
        "right" -> Alignment.CenterEnd
        else -> Alignment.CenterStart
    }
    val buttonModifier = if (context.compact) {
        Modifier
            .heightIn(min = 34.dp)
            .defaultMinSize(minWidth = 64.dp, minHeight = 34.dp)
    } else {
        Modifier
            .heightIn(min = 44.dp)
            .defaultMinSize(minWidth = 96.dp, minHeight = 44.dp)
    }
    val click = { context.runtime.trigger(context.scope, "click", block) }

    Box(
        modifier = (if (context.compact) Modifier else Modifier.fillMaxWidth())
            .padding(vertical = if (context.compact) 2.dp else 0.dp),
        contentAlignment = align,
    ) {
        when (variant) {
            "secondary" -> OutlinedButton(onClick = click, modifier = buttonModifier) {
                Text(label, fontWeight = FontWeight.SemiBold)
            }
            "ghost" -> TextButton(onClick = click, modifier = buttonModifier) {
                Text(label, fontWeight = FontWeight.SemiBold)
            }
            else -> Button(onClick = click, modifier = buttonModifier) {
                Text(label, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}
