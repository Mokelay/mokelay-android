package com.mokelay.android

import com.mokelay.android.api.MokelayPageApi
import com.mokelay.android.dsl.MokelayBlock
import com.mokelay.android.dsl.MokelayPage
import com.mokelay.android.dsl.valueAtPath
import com.mokelay.android.navigation.PageDestination
import com.mokelay.android.navigation.PageSource
import com.mokelay.android.navigation.extractPageDestination
import com.mokelay.android.navigation.manualPageDestination
import com.mokelay.android.runtime.MokelayActionRecord
import com.mokelay.android.runtime.MokelayActionState
import com.mokelay.android.runtime.MokelayBlockRuntimeHandle
import com.mokelay.android.runtime.MokelayDatasource
import com.mokelay.android.runtime.MokelayDatasourceRuntime
import com.mokelay.android.runtime.MokelayProcessors
import com.mokelay.android.runtime.MokelayRuntime
import com.mokelay.android.runtime.MokelayTemplateResolver
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class MokelayRuntimeTest {
    private val servers = mutableListOf<MockWebServer>()
    private val json = MokelayPageApi.defaultJson

    @After
    fun tearDown() {
        servers.forEach { it.shutdown() }
    }

    @Test
    fun targetPageDslDecodesBlocksAndEvents() {
        val listPage = decodeFixturePage("mokelay_list_page")
        val createPage = decodeFixturePage("mokelay_create_page")

        assertEquals("mokelay_list_page", listPage.uuid)
        assertEquals(listOf("MButton", "MAdvanceTable"), listPage.blocks.map { it.type })
        assertEquals("open_dialog", listPage.blocks.first().events.first().actions.first().action)

        assertEquals("mokelay_create_page", createPage.uuid)
        assertEquals(listOf("MForm", "MButton"), createPage.blocks.map { it.type })
        assertEquals(
            listOf("confirm", "if_controller", "execute_ds", "call_block_method"),
            createPage.blocks[1].events.first().actions.map { it.action },
        )
    }

    @Test
    fun previewUrlAndManualCheckboxProduceSystemSource() {
        val systemDestination = extractPageDestination("http://localhost:5173/#/pages/mokelay_list_page/preview?source=system")
        val appRouteDestination = extractPageDestination("/#/pages/index")

        assertEquals(PageDestination("mokelay_list_page", PageSource.SYSTEM), systemDestination)
        assertEquals(PageDestination("index", PageSource.USER), appRouteDestination)
        assertEquals(
            PageDestination("mokelay_list_page", PageSource.SYSTEM),
            manualPageDestination(" mokelay_list_page ", isBuiltinPage = true),
        )
    }

    @Test
    fun templateResolverAndTrimProcessorWork() {
        val sourceBlock = MokelayBlock(
            id = "button",
            type = "MButton",
            data = buildJsonObject {
                put(
                    "action",
                    buildJsonObject {
                        put("uuid", "page-1")
                        put("name", "页面")
                    },
                )
            },
        )
        val state = MokelayActionState(
            blocks = emptyMap(),
            sourceBlock = sourceBlock,
            actions = mutableMapOf(
                "confirm" to MokelayActionRecord(
                    inputs = emptyMap(),
                    outputs = mapOf("result" to JsonPrimitive(true)),
                ),
            ),
        )
        val content = MokelayTemplateResolver.resolve(
            buildJsonObject { put("template", "删除 {{sourceBlock.data.action.name}}") },
            state,
        )
        val routed = MokelayTemplateResolver.resolve(
            buildJsonObject { put("template", "{{actions['confirm'].outputs.result}}") },
            state,
        )
        val trimmed = MokelayProcessors.apply(
            JsonPrimitive("  hello  "),
            listOf(JsonPrimitive("trim")),
        )

        assertEquals(JsonPrimitive("删除 页面"), content)
        assertEquals(JsonPrimitive(true), routed)
        assertEquals(JsonPrimitive("hello"), trimmed)
    }

    @Test
    fun datasourceUrlBodyAndSchemaSelectionWork() {
        val api = MokelayPageApi(baseUrl = "http://127.0.0.1:8787", client = OkHttpClient())
        val tableDatasource = MokelayDatasource.fromJson(
            buildJsonObject {
                put("type", "API")
                put("domain", "mokelay")
                put("path", "/api/mokelay/list_pages")
                put("method", "GET")
                put(
                    "queryData",
                    buildJsonArray {
                        add(variableQuery("page", "table", "page"))
                        add(variableQuery("pageSize", "table", "pageSize"))
                    },
                )
            },
        )!!
        val url = MokelayDatasourceRuntime.requestUrl(
            datasource = tableDatasource,
            apiClient = api,
            blocks = mapOf("table" to mapOf("page" to JsonPrimitive(2), "pageSize" to JsonPrimitive(20))),
        )

        assertTrue(url.startsWith("http://127.0.0.1:8787/api/mokelay/list_pages?"))
        assertTrue(url.contains("page=2"))
        assertTrue(url.contains("pageSize=20"))

        val createDatasource = MokelayDatasource.fromJson(
            buildJsonObject {
                put("type", "API")
                put("domain", "mokelay")
                put("path", "/api/mokelay/create_page")
                put("method", "POST")
                put(
                    "bodyData",
                    buildJsonArray {
                        add(
                            buildJsonObject {
                                put("key", "name")
                                put("dataType", "string")
                                put(
                                    "value",
                                    buildJsonObject {
                                        put("mode", "variable")
                                        put("blockId", "form")
                                        put("variable", "name")
                                        put("processors", buildJsonArray { add(JsonPrimitive("trim")) })
                                    },
                                )
                            },
                        )
                        add(
                            buildJsonObject {
                                put("key", "blocks")
                                put("dataType", "array")
                                put("value", buildJsonArray {})
                            },
                        )
                    },
                )
            },
        )!!
        val body = MokelayDatasourceRuntime.requestBody(
            createDatasource,
            mapOf("form" to mapOf("name" to JsonPrimitive("  New Page  "))),
        ) as JsonObject

        assertEquals(JsonPrimitive("New Page"), body["name"])
        assertEquals(buildJsonArray {}, body["blocks"])

        val rawResponse = buildJsonObject {
            put(
                "data",
                buildJsonObject {
                    put("pages", buildJsonArray { add(buildJsonObject { put("uuid", "index") }) })
                    put("pagination", buildJsonObject { put("page", 1) })
                },
            )
        }
        assertEquals(buildJsonArray { add(buildJsonObject { put("uuid", "index") }) }, rawResponse.valueAtPath("data.pages[]"))
        assertEquals(JsonPrimitive(1), rawResponse.valueAtPath("data.pagination.page"))
    }

    @Test
    fun systemPageApiUsesReadMokelayPageJson() = runTest {
        val server = MockWebServer().also {
            servers += it
            it.enqueue(
                MockResponse().setBody(
                    """{"ok":true,"data":{"page":{"uuid":"mokelay_list_page","name":"页面列表","blocks":[]}}}""",
                ),
            )
            it.start()
        }
        val api = MokelayPageApi(baseUrl = server.url("/").toString(), client = OkHttpClient())
        val page = api.fetchPage("mokelay_list_page", PageSource.SYSTEM)
        val request = server.takeRequest()

        assertEquals("mokelay_list_page", page.uuid)
        assertEquals("/api/mokelay/read_mokelay_page_json?uuid=mokelay_list_page", request.path)
    }

    @Test
    fun datasourceRuntimeExecutesListPagesRequest() = runTest {
        val server = MockWebServer().also {
            servers += it
            it.enqueue(
                MockResponse().setBody(
                    """
                    {
                      "ok": true,
                      "data": {
                        "pages": [{"uuid":"index","name":"首页"}],
                        "pagination": {"page":1,"pageSize":10,"total":1}
                      }
                    }
                    """.trimIndent(),
                ),
            )
            it.start()
        }
        val api = MokelayPageApi(baseUrl = server.url("/").toString(), client = OkHttpClient())
        val datasource = MokelayDatasource.fromJson(
            buildJsonObject {
                put("type", "API")
                put("domain", "mokelay")
                put("path", "/api/mokelay/list_pages")
                put("method", "GET")
                put("queryData", buildJsonArray {
                    add(variableQuery("page", "table", "page"))
                    add(variableQuery("pageSize", "table", "pageSize"))
                })
                put("matchingExternalFields", buildJsonArray {
                    add(matchField("data", "data.pages[]"))
                    add(matchField("page", "data.pagination.page"))
                    add(matchField("pageSize", "data.pagination.pageSize"))
                    add(matchField("total", "data.pagination.total"))
                })
            },
        )!!

        val result = MokelayDatasourceRuntime.execute(
            datasource = datasource,
            apiClient = api,
            blocks = mapOf("table" to mapOf("page" to JsonPrimitive(1), "pageSize" to JsonPrimitive(10))),
        )
        val request = server.takeRequest()

        assertEquals("/api/mokelay/list_pages?page=1&pageSize=10", request.path)
        assertEquals(JsonPrimitive(1), result.matchingExternalFieldData["page"])
        assertEquals(JsonPrimitive(1), result.matchingExternalFieldData["total"])
    }

    @Test
    fun actionGraphConfirmDeleteAndRefreshTable() = runTest {
        val server = MockWebServer().also {
            servers += it
            it.enqueue(MockResponse().setBody("""{"ok":true,"data":{"affected":1}}"""))
            it.start()
        }
        val api = MokelayPageApi(baseUrl = server.url("/").toString(), client = OkHttpClient())
        val runtime = MokelayRuntime(apiClient = api, onNavigatePage = {}, onOpenExternalUrl = {})
        var didRefresh = false

        runtime.register(
            MokelayBlockRuntimeHandle(
                id = "table",
                type = "MAdvanceTable",
                getData = {
                    mapOf(
                        "page" to JsonPrimitive(1),
                        "pageSize" to JsonPrimitive(10),
                        "total" to JsonPrimitive(1),
                    )
                },
                callMethod = { method, _ ->
                    if (method == "refresh") {
                        didRefresh = true
                        buildJsonObject { put("ok", true) }
                    } else {
                        null
                    }
                },
            ),
        )
        val sourceBlock = MokelayBlock(
            id = "delete",
            type = "MButton",
            data = buildJsonObject {
                put("action", buildJsonObject {
                    put("uuid", "page-1")
                    put("name", "页面")
                })
            },
        )
        val actions = decodeActions(
            """
            [
              {
                "uuid": "confirm",
                "action": "confirm",
                "inputs": {
                  "title": "确认删除页面",
                  "content": {"template": "确定要删除页面「{{sourceBlock.data.action.name}}」吗？"}
                },
                "outputs": ["result"],
                "nextAction": "route"
              },
              {
                "uuid": "route",
                "action": "if_controller",
                "inputs": {"value": {"template": "{{actions['confirm'].outputs.result}}"}},
                "nodes": [
                  {"uuid":"true","value":true,"nextAction":"delete_ds"},
                  {"uuid":"false","value":false,"nextAction":null}
                ]
              },
              {
                "uuid": "delete_ds",
                "action": "execute_ds",
                "inputs": {
                  "dsConfig": {
                    "type": "API",
                    "domain": "mokelay",
                    "path": "/api/mokelay/delete_page_by_uuid",
                    "method": "POST",
                    "bodyData": [
                      {"key":"uuid","dataType":"string","value":{"template":"{{sourceBlock.data.action.uuid}}"}}
                    ]
                  }
                },
                "outputs": ["rawResponse"],
                "nextAction": "refresh"
              },
              {
                "uuid": "refresh",
                "action": "call_block_method",
                "inputs": {"blockId": "table", "method": "refresh"},
                "outputs": ["returnData"]
              }
            ]
            """.trimIndent(),
        )

        val graph = async { runtime.runActionGraph(actions, sourceBlock) }
        runCurrent()
        val confirm = requireNotNull(runtime.confirmRequest)
        runtime.resolveConfirm(confirm, true)
        graph.await()
        val request = server.takeRequest()

        assertEquals("/api/mokelay/delete_page_by_uuid", request.path)
        assertTrue(request.body.readUtf8().contains("page-1"))
        assertTrue(didRefresh)
    }

    private fun decodeFixturePage(uuid: String): MokelayPage {
        val fixture = fixtureFile("$uuid.json")
        return json.decodeFromString(MokelayPage.serializer(), fixture.readText())
    }

    private fun fixtureFile(name: String): File {
        val root = File(System.getProperty("user.dir"))
        val candidates = listOf(
            File(root, "../mokelay-server/server/assets/mokelay/pages/$name"),
            File(root, "../../mokelay-server/server/assets/mokelay/pages/$name"),
            File(root, "submodule/mokelay-server/server/assets/mokelay/pages/$name"),
        )
        return candidates.firstOrNull { it.isFile }
            ?: error("Fixture not found: $name from ${root.absolutePath}")
    }

    private fun variableQuery(key: String, blockId: String, variable: String): JsonObject {
        return buildJsonObject {
            put("key", key)
            put(
                "value",
                buildJsonObject {
                    put("mode", "variable")
                    put("blockId", blockId)
                    put("variable", variable)
                },
            )
        }
    }

    private fun matchField(variable: String, path: String): JsonObject {
        return buildJsonObject {
            put("label", variable)
            put("variable", variable)
            put("matchFieldPath", path)
        }
    }

    private fun decodeActions(raw: String): List<com.mokelay.android.dsl.MokelayActionConfig> {
        val element = json.decodeFromString(JsonElement.serializer(), raw)
        return element
            .let { it as kotlinx.serialization.json.JsonArray }
            .mapNotNull(com.mokelay.android.dsl.MokelayActionConfig::fromJsonElement)
    }
}
