package com.mokelay.android.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.mokelay.android.api.MokelayPageApi
import com.mokelay.android.dsl.MokelayPage
import com.mokelay.android.navigation.PageDestination
import com.mokelay.android.navigation.PageSource
import com.mokelay.android.runtime.MokelayRuntime
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PageScreen(
    uuid: String,
    source: PageSource,
    apiClient: MokelayPageApi,
    onBack: () -> Unit,
    onNavigatePage: (PageDestination) -> Unit,
    onOpenExternalUrl: (String) -> Unit,
) {
    val snackbarHostState = remember { SnackbarHostState() }
    val runtime = remember(apiClient) {
        MokelayRuntime(
            apiClient = apiClient,
            onNavigatePage = onNavigatePage,
            onOpenExternalUrl = onOpenExternalUrl,
        )
    }
    var loadState by remember(uuid, source) { mutableStateOf<PageLoadState>(PageLoadState.Loading) }

    fun loadPage() {
        loadState = PageLoadState.Loading
    }

    LaunchedEffect(uuid, source, loadState) {
        if (loadState == PageLoadState.Loading) {
            loadState = try {
                PageLoadState.Loaded(apiClient.fetchPage(uuid, source))
            } catch (error: Throwable) {
                PageLoadState.Error(error.localizedMessage ?: error.toString())
            }
        }
    }

    LaunchedEffect(runtime.errorMessage) {
        val message = runtime.errorMessage
        if (!message.isNullOrBlank()) {
            snackbarHostState.showSnackbar(message)
            runtime.clearError()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("Mokelay") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "返回")
                    }
                },
            )
        },
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            when (val state = loadState) {
                PageLoadState.Loading -> CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                is PageLoadState.Error -> ErrorState(
                    message = state.message,
                    onRetry = ::loadPage,
                    modifier = Modifier.align(Alignment.Center),
                )
                is PageLoadState.Loaded -> PageRenderer(
                    page = state.page,
                    runtime = runtime,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
    }

    RuntimeOverlays(
        runtime = runtime,
        apiClient = apiClient,
    )
}

@Composable
private fun RuntimeOverlays(
    runtime: MokelayRuntime,
    apiClient: MokelayPageApi,
) {
    runtime.confirmRequest?.let { request ->
        AlertDialog(
            onDismissRequest = { runtime.resolveConfirm(request, false) },
            title = { Text(request.title) },
            text = { Text(request.content) },
            confirmButton = {
                Button(onClick = { runtime.resolveConfirm(request, true) }) {
                    Text("确定")
                }
            },
            dismissButton = {
                TextButton(onClick = { runtime.resolveConfirm(request, false) }) {
                    Text("取消")
                }
            },
        )
    }

    runtime.dialogRequest?.let { request ->
        Dialog(
            onDismissRequest = {
                runtime.closeDialog(buildJsonObject { put("result", false) })
            },
            properties = DialogProperties(usePlatformDefaultWidth = false),
        ) {
            DialogPage(
                title = request.title,
                uuid = request.pageUUID,
                source = request.pageSource,
                apiClient = apiClient,
                parentRuntime = runtime,
                onDismiss = {
                    runtime.closeDialog(buildJsonObject { put("result", false) })
                },
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DialogPage(
    title: String,
    uuid: String,
    source: PageSource,
    apiClient: MokelayPageApi,
    parentRuntime: MokelayRuntime,
    onDismiss: () -> Unit,
) {
    var loadState by remember(uuid, source) { mutableStateOf<PageLoadState>(PageLoadState.Loading) }

    LaunchedEffect(uuid, source) {
        loadState = try {
            PageLoadState.Loaded(apiClient.fetchPage(uuid, source))
        } catch (error: Throwable) {
            PageLoadState.Error(error.localizedMessage ?: error.toString())
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text(title.ifBlank { "Mokelay" }) },
                actions = {
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "关闭")
                    }
                },
            )
        },
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(padding),
        ) {
            when (val state = loadState) {
                PageLoadState.Loading -> CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                is PageLoadState.Error -> ErrorState(
                    message = state.message,
                    onRetry = { loadState = PageLoadState.Loading },
                    modifier = Modifier.align(Alignment.Center),
                )
                is PageLoadState.Loaded -> PageRenderer(
                    page = state.page,
                    runtime = parentRuntime,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }

}

@Composable
private fun ErrorState(
    message: String,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text(
            text = "页面加载失败",
            style = MaterialTheme.typography.titleLarge,
        )
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Button(onClick = onRetry) {
            Text("重试")
        }
    }
}

private sealed interface PageLoadState {
    data object Loading : PageLoadState
    data class Loaded(val page: MokelayPage) : PageLoadState
    data class Error(val message: String) : PageLoadState
}
