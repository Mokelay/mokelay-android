package com.mokelay.android.ui.blocks

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import com.mokelay.android.dsl.MokelayBlock
import com.mokelay.android.dsl.displayString
import com.mokelay.android.ui.RenderContext

@Composable
fun ParagraphBlock(block: MokelayBlock, context: RenderContext) {
    Text(
        text = block.data["text"].displayString().plainTextFromHtml(),
        style = if (context.compact) MaterialTheme.typography.bodySmall else MaterialTheme.typography.bodyMedium,
    )
}

fun String.plainTextFromHtml(): String {
    return replace(Regex("<br\\s*/?>", RegexOption.IGNORE_CASE), "\n")
        .replace(Regex("<[^>]+>"), "")
        .replace("&nbsp;", " ")
        .replace("&amp;", "&")
        .replace("&lt;", "<")
        .replace("&gt;", ">")
        .trim()
}
