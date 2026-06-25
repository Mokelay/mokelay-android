package com.mokelay.android.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ElevatedButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mokelay.android.navigation.PageDestination
import com.mokelay.android.navigation.extractPageDestination
import com.mokelay.android.navigation.manualPageDestination
import com.mokelay.android.ui.scanner.QrScannerDialog

@Composable
fun HomeScreen(onOpenPage: (PageDestination) -> Unit) {
    var pageUUID by remember { mutableStateOf("") }
    var isBuiltinPage by remember { mutableStateOf(false) }
    var scanErrorMessage by remember { mutableStateOf("") }
    var isScannerVisible by remember { mutableStateOf(false) }
    val trimmedUUID = pageUUID.trim()

    Surface(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Logo()

            Spacer(modifier = Modifier.height(36.dp))

            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(14.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                OutlinedTextField(
                    value = pageUUID,
                    onValueChange = { pageUUID = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    leadingIcon = {
                        Icon(Icons.Default.Search, contentDescription = null)
                    },
                    placeholder = { Text("请输入页面UUID") },
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { isBuiltinPage = !isBuiltinPage },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Checkbox(
                        checked = isBuiltinPage,
                        onCheckedChange = { isBuiltinPage = it },
                    )
                    Text(
                        text = "是否内置页面",
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                    )
                }

                Button(
                    onClick = {
                        onOpenPage(manualPageDestination(trimmedUUID, isBuiltinPage))
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(54.dp),
                    enabled = trimmedUUID.isNotEmpty(),
                ) {
                    Text("跳转", fontWeight = FontWeight.SemiBold)
                }

                ElevatedButton(
                    onClick = {
                        scanErrorMessage = ""
                        isScannerVisible = true
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(54.dp),
                ) {
                    Icon(Icons.Default.QrCodeScanner, contentDescription = null)
                    Text(
                        text = "扫描二维码",
                        modifier = Modifier.padding(start = 8.dp),
                        fontWeight = FontWeight.SemiBold,
                    )
                }

                if (scanErrorMessage.isNotEmpty()) {
                    Text(
                        text = scanErrorMessage,
                        color = MaterialTheme.colorScheme.error,
                        textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }
    }

    if (isScannerVisible) {
        QrScannerDialog(
            onDismiss = { isScannerVisible = false },
            onCodeScanned = { scannedText ->
                val destination = extractPageDestination(scannedText)
                if (destination == null) {
                    scanErrorMessage = "未识别到 Mokelay 预览地址"
                    isScannerVisible = false
                } else {
                    pageUUID = destination.uuid
                    isBuiltinPage = destination.source == com.mokelay.android.navigation.PageSource.SYSTEM
                    scanErrorMessage = ""
                    isScannerVisible = false
                    onOpenPage(destination)
                }
            },
        )
    }
}

@Composable
private fun Logo() {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier
                .size(108.dp)
                .clip(RoundedCornerShape(26.dp))
                .background(Color(0xFF0F3028)),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "M",
                color = Color(0xFFD7F66B),
                fontSize = 70.sp,
                fontWeight = FontWeight.Black,
            )
        }

        Spacer(modifier = Modifier.height(18.dp))

        Text(
            text = "Mokelay",
            style = MaterialTheme.typography.displaySmall.copy(fontWeight = FontWeight.Bold),
        )
    }
}
