package com.mokelay.android.runtime

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.mokelay.android.api.MokelayPageApi
import com.mokelay.android.dsl.MokelayActionConfig
import com.mokelay.android.dsl.MokelayBlock
import com.mokelay.android.dsl.displayString
import com.mokelay.android.dsl.objectOrNull
import com.mokelay.android.dsl.stringOrNull
import com.mokelay.android.dsl.truthy
import com.mokelay.android.navigation.PageDestination
import com.mokelay.android.navigation.PageSource
import com.mokelay.android.navigation.extractPageDestination
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

data class MokelayBlockRuntimeHandle(
    val id: String,
    val type: String,
    val getData: suspend () -> Map<String, JsonElement>,
    val callMethod: suspend (method: String, params: JsonObject?) -> JsonElement?,
)

data class MokelayConfirmRequest(
    val title: String,
    val content: String,
    val deferred: CompletableDeferred<Boolean>,
)

data class MokelayDialogRequest(
    val title: String,
    val pageUUID: String,
    val pageSource: PageSource,
    val deferred: CompletableDeferred<JsonElement>,
)

class MokelayRuntime(
    val apiClient: MokelayPageApi,
    private val onNavigatePage: (PageDestination) -> Unit,
    private val onOpenExternalUrl: (String) -> Unit,
) {
    private val handles = mutableMapOf<String, MokelayBlockRuntimeHandle>()

    var confirmRequest by mutableStateOf<MokelayConfirmRequest?>(null)
        private set

    var dialogRequest by mutableStateOf<MokelayDialogRequest?>(null)
        private set

    var errorMessage by mutableStateOf<String?>(null)
        private set

    fun register(handle: MokelayBlockRuntimeHandle) {
        handles[handle.id] = handle
    }

    fun unregister(id: String) {
        handles.remove(id)
    }

    suspend fun getBlockDataContext(excluding: String? = null): Map<String, Map<String, JsonElement>> {
        val data = mutableMapOf<String, Map<String, JsonElement>>()
        handles.values.forEach { handle ->
            if (handle.id != excluding) {
                data[handle.id] = handle.getData()
            }
        }
        return data
    }

    suspend fun callBlockMethod(blockId: String, method: String, params: JsonObject? = null): JsonElement {
        val handle = handles[blockId] ?: return JsonNull
        return handle.callMethod(method, params) ?: JsonNull
    }

    fun trigger(scope: CoroutineScope, eventName: String, block: MokelayBlock) {
        val eventConfig = block.events.firstOrNull { it.event == eventName } ?: return
        scope.launch {
            try {
                runActionGraph(eventConfig.actions, block)
            } catch (error: Throwable) {
                errorMessage = error.localizedMessage ?: error.toString()
            }
        }
    }

    suspend fun runActionGraph(actions: List<MokelayActionConfig>, sourceBlock: MokelayBlock) {
        if (actions.isEmpty()) return

        val actionsById = actions.associateBy { it.uuid }
        var currentId: String? = actions.first().uuid
        var state = MokelayActionState(
            blocks = getBlockDataContext(),
            sourceBlock = sourceBlock,
        )
        var guard = 0

        while (!currentId.isNullOrBlank() && guard < 100) {
            guard += 1
            val action = actionsById[currentId] ?: break
            state.blocks = getBlockDataContext()
            val inputs = action.inputs.mapValues { (_, input) ->
                MokelayTemplateResolver.resolve(input, state)
            }
            val outcome = executeAction(action, inputs, state)
            state.actions[action.uuid] = MokelayActionRecord(
                inputs = inputs,
                outputs = outcome.outputs,
            )
            currentId = outcome.nextAction ?: action.nextAction
        }
    }

    fun resolveConfirm(request: MokelayConfirmRequest, result: Boolean) {
        if (confirmRequest === request) {
            confirmRequest = null
        }
        request.deferred.complete(result)
    }

    fun closeDialog(result: JsonElement = buildJsonObject { put("result", true) }) {
        val request = dialogRequest ?: return
        dialogRequest = null
        request.deferred.complete(result)
    }

    fun clearError() {
        errorMessage = null
    }

    private suspend fun executeAction(
        action: MokelayActionConfig,
        inputs: Map<String, JsonElement>,
        state: MokelayActionState,
    ): ActionOutcome {
        return when (action.action) {
            "confirm" -> {
                val title = inputs["title"].displayString().ifEmpty { "确认" }
                val content = inputs["content"].displayString()
                val result = awaitConfirm(title, content)
                ActionOutcome(outputs = mapOf("result" to JsonPrimitive(result)))
            }
            "open_dialog" -> {
                val title = inputs["title"].displayString()
                val pageUUID = inputs["pageUUID"].displayString()
                val pageSource = PageSource.fromWireValue(inputs["pageSource"].stringOrNull())
                val closeResult = awaitDialog(title, pageUUID, pageSource)
                ActionOutcome(outputs = mapOf("close_result" to closeResult))
            }
            "execute_ds" -> {
                val dsConfig = inputs["dsConfig"] ?: inputs["value"] ?: JsonObject(inputs)
                val datasource = MokelayDatasource.fromJson(dsConfig)
                    ?: throw IllegalArgumentException("Datasource config is invalid.")
                val runtimeData = MokelayDatasourceRuntime.execute(
                    datasource = datasource,
                    apiClient = apiClient,
                    blocks = getBlockDataContext(),
                )
                val outputs = mutableMapOf<String, JsonElement>("rawResponse" to runtimeData.rawResponse)
                outputs += runtimeData.schemaSelectionData
                outputs += runtimeData.matchingExternalFieldData
                ActionOutcome(outputs = outputs)
            }
            "call_block_method" -> {
                val blockId = inputs["blockId"].displayString()
                val method = inputs["method"].displayString()
                val params = inputs["params"].objectOrNull()
                val returnData = callBlockMethod(blockId, method, params)
                ActionOutcome(outputs = mapOf("returnData" to returnData))
            }
            "if_controller" -> {
                val value = inputs["value"] ?: JsonNull
                val selected = action.nodes.firstOrNull { node ->
                    (node.value ?: JsonNull).truthy() == value.truthy()
                }
                ActionOutcome(
                    outputs = mapOf("value" to value),
                    nextAction = selected?.nextAction,
                )
            }
            "jump_url" -> {
                val url = inputs["url"].displayString()
                val destination = extractPageDestination(url)
                if (destination != null) {
                    onNavigatePage(destination)
                } else {
                    onOpenExternalUrl(url)
                }
                ActionOutcome(outputs = mapOf("url" to JsonPrimitive(url)))
            }
            else -> ActionOutcome(outputs = emptyMap())
        }
    }

    private suspend fun awaitConfirm(title: String, content: String): Boolean {
        val deferred = CompletableDeferred<Boolean>()
        confirmRequest = MokelayConfirmRequest(title, content, deferred)
        return deferred.await()
    }

    private suspend fun awaitDialog(title: String, pageUUID: String, pageSource: PageSource): JsonElement {
        val deferred = CompletableDeferred<JsonElement>()
        dialogRequest = MokelayDialogRequest(title, pageUUID, pageSource, deferred)
        return deferred.await()
    }
}

private data class ActionOutcome(
    val outputs: Map<String, JsonElement>,
    val nextAction: String? = null,
)
