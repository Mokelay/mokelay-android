package com.mokelay.android.ui.blocks

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.mokelay.android.dsl.MokelayBlock
import com.mokelay.android.dsl.arrayOrNull
import com.mokelay.android.dsl.booleanOrNullValue
import com.mokelay.android.dsl.intOrNull
import com.mokelay.android.dsl.objectOrNull
import com.mokelay.android.dsl.stringOrNull
import com.mokelay.android.runtime.MokelayBlockRuntimeHandle
import com.mokelay.android.runtime.MokelayDatasource
import com.mokelay.android.runtime.MokelayDatasourceRuntime
import com.mokelay.android.runtime.MokelayTemplateResolver
import com.mokelay.android.ui.RenderBlock
import com.mokelay.android.ui.RenderContext
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

private data class MAdvanceTableColumn(
    val columnName: String,
    val width: Dp?,
    val fieldVariable: String,
    val columnContent: List<MokelayBlock>,
)

@Composable
fun MAdvanceTableBlock(block: MokelayBlock, context: RenderContext) {
    val model = remember(block.id) {
        MAdvanceTableModel(block = block, context = context)
    }
    val scope = rememberCoroutineScope()
    val scrollState = rememberScrollState()

    DisposableEffect(block.id) {
        context.runtime.register(
            MokelayBlockRuntimeHandle(
                id = block.id,
                type = block.type,
                getData = { model.getData() },
                callMethod = { method, _ ->
                    when (method) {
                        "refresh" -> {
                            model.refresh()
                            buildJsonObject { put("ok", true) }
                        }
                        "getData" -> JsonObject(model.getData())
                        else -> null
                    }
                },
            ),
        )
        onDispose {
            context.runtime.unregister(block.id)
        }
    }

    LaunchedEffect(block.id) {
        model.refresh()
    }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        if (model.errorMessage != null) {
            Text(
                text = model.errorMessage.orEmpty(),
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
            )
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .border(BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)),
        ) {
            Column(
                modifier = Modifier
                    .horizontalScroll(scrollState)
                    .defaultMinSize(minWidth = 640.dp),
            ) {
                TableHeader(columns = model.columns)
                model.rows.forEachIndexed { index, row ->
                    TableRow(
                        row = row,
                        rowIndex = index,
                        columns = model.columns,
                        context = context.compact(),
                    )
                }
                if (model.rows.isEmpty() && !model.isLoading) {
                    Text(
                        text = "暂无数据",
                        modifier = Modifier.padding(16.dp),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            if (model.isLoading) {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
            }
        }

        if (model.showPageBreak) {
            PaginationBar(
                page = model.page,
                pageSize = model.pageSize,
                total = model.total,
                onPrevious = {
                    if (model.page > 1) {
                        model.page -= 1
                        scope.launch { model.refresh() }
                    }
                },
                onNext = {
                    if (model.page * model.pageSize < model.total) {
                        model.page += 1
                        scope.launch { model.refresh() }
                    }
                },
            )
        }
    }
}

private class MAdvanceTableModel(
    private val block: MokelayBlock,
    private val context: RenderContext,
) {
    val columns: List<MAdvanceTableColumn> = block.data["columns"].arrayOrNull()?.mapNotNull { column ->
        val obj = column.objectOrNull() ?: return@mapNotNull null
        MAdvanceTableColumn(
            columnName = obj["columnName"].stringOrNull().orEmpty(),
            width = obj["width"].intOrNull()?.dp,
            fieldVariable = obj["fieldVariable"].stringOrNull().orEmpty(),
            columnContent = obj["columnContent"].arrayOrNull()
                ?.mapNotNull(MokelayBlock::fromJsonElement)
                ?: emptyList(),
        )
    } ?: emptyList()
    val showPageBreak: Boolean = block.data["showPageBreak"].booleanOrNullValue()
        ?: block.data["showPageBreak"].stringOrNull()?.toBooleanStrictOrNull()
        ?: false
    private val datasource = MokelayDatasource.fromJson(block.data["ds"])

    var page by mutableIntStateOf(1)
    var pageSize by mutableIntStateOf(10)
    var total by mutableIntStateOf(0)
    var rows by mutableStateOf<List<JsonObject>>(emptyList())
        private set
    var isLoading by mutableStateOf(false)
        private set
    var errorMessage by mutableStateOf<String?>(null)
        private set

    suspend fun refresh() {
        isLoading = true
        errorMessage = null

        try {
            val ds = datasource
            if (ds == null) {
                rows = emptyList()
                return
            }
            val blockContext = context.runtime.getBlockDataContext(excluding = block.id).toMutableMap()
            blockContext[block.id] = getData()
            val runtimeData = MokelayDatasourceRuntime.execute(
                datasource = ds,
                apiClient = context.runtime.apiClient,
                blocks = blockContext,
            )
            rows = runtimeData.matchingExternalFieldData["data"]
                ?.arrayOrNull()
                ?.mapNotNull { it.objectOrNull() }
                ?: emptyList()
            page = runtimeData.matchingExternalFieldData["page"]?.intOrNull() ?: page
            pageSize = runtimeData.matchingExternalFieldData["pageSize"]?.intOrNull() ?: pageSize
            total = runtimeData.matchingExternalFieldData["total"]?.intOrNull() ?: rows.size
        } catch (error: Throwable) {
            errorMessage = error.localizedMessage ?: error.toString()
        } finally {
            isLoading = false
        }
    }

    fun getData(): Map<String, JsonElement> = mapOf(
        "page" to JsonPrimitive(page),
        "pageSize" to JsonPrimitive(pageSize),
        "total" to JsonPrimitive(total),
        "data" to JsonArray(rows),
    )
}

@Composable
private fun TableHeader(columns: List<MAdvanceTableColumn>) {
    Row(
        modifier = Modifier
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .border(BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant)),
    ) {
        columns.forEach { column ->
            TableCell(width = column.width ?: 160.dp) {
                Text(
                    text = column.columnName,
                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun TableRow(
    row: JsonObject,
    rowIndex: Int,
    columns: List<MAdvanceTableColumn>,
    context: RenderContext,
) {
    Row(
        modifier = Modifier.border(BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant)),
    ) {
        columns.forEach { column ->
            TableCell(width = column.width ?: 160.dp) {
                val contentBlocks = cellBlocks(row = row, column = column, rowIndex = rowIndex)
                if (contentBlocks.isEmpty()) {
                    Text(row[column.fieldVariable].stringOrNull().orEmpty())
                } else {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        contentBlocks.forEach { block ->
                            RenderBlock(block = block, context = context)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TableCell(width: Dp, content: @Composable () -> Unit) {
    Box(
        modifier = Modifier
            .width(width)
            .padding(horizontal = 10.dp, vertical = 9.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        content()
    }
}

@Composable
private fun PaginationBar(
    page: Int,
    pageSize: Int,
    total: Int,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.End,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "第 $page 页 / 共 $total 条",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.width(12.dp))
        OutlinedButton(
            onClick = onPrevious,
            enabled = page > 1,
            colors = ButtonDefaults.outlinedButtonColors(),
        ) {
            Text("上一页")
        }
        Spacer(modifier = Modifier.width(8.dp))
        OutlinedButton(
            onClick = onNext,
            enabled = page * pageSize < total,
        ) {
            Text("下一页")
        }
    }
}

private fun cellBlocks(
    row: JsonObject,
    column: MAdvanceTableColumn,
    rowIndex: Int,
): List<MokelayBlock> {
    val rowWithIndex = JsonObject(row + ("index" to JsonPrimitive(rowIndex + 1)))
    return column.columnContent.map { block ->
        block.copy(data = MokelayTemplateResolver.interpolate(block.data, rowWithIndex) as? JsonObject ?: block.data)
    }
}
