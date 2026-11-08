package com.example.picowkeyboard.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.picowkeyboard.MainViewModel
import com.example.picowkeyboard.ble.BleConnectionState
import com.example.picowkeyboard.ble.BleDevice
import com.example.picowkeyboard.ble.SendState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    viewModel: MainViewModel,
    onEnsureBluetoothEnabled: () -> Unit = {},
) {
    val scanResults by viewModel.scanResults.collectAsState()
    val isScanning by viewModel.isScanning.collectAsState()
    val connectionState by viewModel.connectionState.collectAsState()
    val sendState by viewModel.sendState.collectAsState()
    val selectedDevice by viewModel.selectedDevice.collectAsState()
    val text by viewModel.text.collectAsState()
    val showAllDevices by viewModel.showAllDevices.collectAsState()

    var showHex by remember { mutableStateOf(false) }

    // Filter to PICO devices unless the debug "show all" toggle is on.
    val visibleDevices = remember(scanResults, showAllDevices) {
        if (showAllDevices) scanResults
        else scanResults.filter {
            it.name?.contains("PICO-W-KEYBOARD", ignoreCase = true) == true
        }
    }

    Scaffold { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "Pico W 字符注入器",
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center
            )

            // ---------------- Device section ----------------
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text("目标蓝牙设备", style = MaterialTheme.typography.titleSmall)

                    DeviceDropdown(
                        devices = visibleDevices,
                        selected = selectedDevice,
                        onSelect = { viewModel.selectDevice(it) },
                        isScanning = isScanning
                    )

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        OutlinedButton(
                            onClick = {
                                onEnsureBluetoothEnabled()
                                viewModel.startScan()
                            },
                            enabled = !isScanning,
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(if (isScanning) "扫描中…" else "扫描设备")
                        }
                        Button(
                            onClick = {
                                onEnsureBluetoothEnabled()
                                viewModel.connectSelected()
                            },
                            enabled = selectedDevice != null,
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("连接")
                        }
                    }

                    // Debug toggle: show all BLE devices.
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Checkbox(
                            checked = showAllDevices,
                            onCheckedChange = { viewModel.showAllDevices.value = it }
                        )
                        Text("显示全部设备（调试）", style = MaterialTheme.typography.bodySmall)
                    }

                    HorizontalDivider()

                    ConnectionStatusLine(connectionState)
                }
            }

            // ---------------- Text input section ----------------
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = text,
                        onValueChange = { viewModel.updateText(it) },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 6,
                        maxLines = 10,
                        placeholder = { Text("在此输入或粘贴文字") },
                        label = { Text("文本") }
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = { viewModel.sendText() },
                            enabled = text.isNotEmpty() &&
                                connectionState is BleConnectionState.Connected &&
                                sendState !is SendState.Sending,
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("发送")
                        }
                        OutlinedButton(
                            onClick = { viewModel.cancelSend() },
                            enabled = sendState is SendState.Sending,
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("取消")
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            "字符数：${viewModel.characterCount()}   UTF-8 字节：${viewModel.byteCount()}",
                            style = MaterialTheme.typography.bodySmall
                        )
                        Text(
                            "分包上限：${viewModel.currentMaxPayload()}B/包",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }

                    SendStatusLine(sendState, viewModel)
                }
            }

            // ---------------- Hex simulation section ----------------
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text("测试模式", style = MaterialTheme.typography.titleSmall)
                        TextButton(onClick = { showHex = !showHex }) {
                            Text(if (showHex) "隐藏 Hex" else "模拟发送")
                        }
                    }
                    if (showHex) {
                        Text(
                            "文本：",
                            style = MaterialTheme.typography.bodySmall
                        )
                        Text(
                            text = "\"${text.take(60)}${if (text.length > 60) "…" else ""}\"",
                            style = MaterialTheme.typography.bodySmall
                        )
                        Text(
                            "UTF-8：",
                            style = MaterialTheme.typography.bodySmall
                        )
                        SelectionContainer {
                            Text(
                                text = viewModel.utf8Hex().ifEmpty { "（空）" },
                                style = MaterialTheme.typography.bodySmall,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DeviceDropdown(
    devices: List<BleDevice>,
    selected: BleDevice?,
    onSelect: (BleDevice?) -> Unit,
    isScanning: Boolean,
) {
    var expanded by remember { mutableStateOf(false) }

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it }
    ) {
        OutlinedTextField(
            value = selected?.displayName ?: "",
            onValueChange = {},
            readOnly = true,
            placeholder = { Text(if (isScanning) "正在扫描…" else "未选择设备") },
            trailingIcon = {
                Icon(
                    imageVector = Icons.Default.ArrowDropDown,
                    contentDescription = null,
                    modifier = Modifier
                        .padding(end = 8.dp)
                        .then(Modifier)
                )
            },
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(),
            colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors()
        )

        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            if (devices.isEmpty()) {
                DropdownMenuItem(
                    text = { Text(if (isScanning) "扫描中，暂无设备" else "点击“扫描设备”开始") },
                    onClick = {}
                )
            } else {
                devices.forEach { device ->
                    DropdownMenuItem(
                        text = {
                            Column {
                                Text(device.name ?: "（无名称）")
                                Text(
                                    "${device.address}  RSSI: ${device.rssi}",
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        },
                        onClick = {
                            onSelect(device)
                            expanded = false
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun ConnectionStatusLine(state: BleConnectionState) {
    val (label, color) = when (state) {
        is BleConnectionState.Disconnected -> "状态：未连接" to MaterialTheme.colorScheme.onSurfaceVariant
        is BleConnectionState.Scanning -> "状态：扫描中…" to MaterialTheme.colorScheme.primary
        is BleConnectionState.Connecting -> "状态：连接中…" to MaterialTheme.colorScheme.primary
        is BleConnectionState.Connected -> "状态：已连接：${state.device.displayName}" to MaterialTheme.colorScheme.primary
        is BleConnectionState.Disconnecting -> "状态：断开中…" to MaterialTheme.colorScheme.tertiary
    }
    Text(
        text = label,
        style = MaterialTheme.typography.bodyMedium,
        color = color
    )
}

@Composable
private fun SendStatusLine(state: SendState, viewModel: MainViewModel) {
    val (label, color) = when (state) {
        is SendState.Idle -> "状态：等待发送" to MaterialTheme.colorScheme.onSurfaceVariant
        is SendState.Sending -> "状态：发送中 ${state.sentPackets}/${state.totalPackets} 包" to
            MaterialTheme.colorScheme.primary
        is SendState.Success -> "状态：已发送 ${state.totalBytes} 字节（${state.totalPackets} 包）" to
            MaterialTheme.colorScheme.primary
        is SendState.Failed -> "状态：发送失败：${state.reason}" to
            MaterialTheme.colorScheme.error
        is SendState.Cancelled -> "状态：已取消" to MaterialTheme.colorScheme.tertiary
    }
    Text(
        text = label,
        style = MaterialTheme.typography.bodyMedium,
        color = color
    )
}
