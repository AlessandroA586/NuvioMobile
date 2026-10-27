package com.nuvio.app.features.cast

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cast
import androidx.compose.material.icons.filled.CastConnected
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nuvio.app.core.ui.NuvioModalBottomSheet
import com.nuvio.app.core.ui.NuvioTokens

@Composable
fun CastIconButton(
    modifier: Modifier = Modifier,
    tint: Color = Color.White,
) {
    var showSheet by remember { mutableStateOf(false) }
    val selectedDevice by CastManager.selectedDevice.collectAsState()
    val isConnecting by CastManager.isConnecting.collectAsState()

    val isConnected = selectedDevice != null
    val icon = if (isConnected) Icons.Filled.CastConnected else Icons.Filled.Cast
    val activeTint = if (isConnected) MaterialTheme.colorScheme.primary else tint

    IconButton(
        onClick = { showSheet = true },
        modifier = modifier,
    ) {
        if (isConnecting) {
            CircularProgressIndicator(
                modifier = Modifier.size(20.dp),
                strokeWidth = 2.dp,
                color = activeTint,
            )
        } else {
            Icon(
                imageVector = icon,
                contentDescription = "Cast to TV",
                tint = activeTint,
                modifier = Modifier.size(24.dp),
            )
        }
    }

    if (showSheet) {
        CastDeviceSheet(onDismiss = { showSheet = false })
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CastDeviceSheet(
    onDismiss: () -> Unit,
) {
    val devices by CastManager.availableDevices.collectAsState()
    val selectedDevice by CastManager.selectedDevice.collectAsState()
    val playbackState by CastManager.playbackState.collectAsState()
    var manualIp by remember { mutableStateOf("") }
    var isAddingManual by remember { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    NuvioModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = NuvioTokens.Space.s20, vertical = NuvioTokens.Space.s16),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Transmitir a Smart TV",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                )
                if (selectedDevice != null) {
                    Text(
                        text = "Desconectar",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { CastManager.selectDevice(null) }
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                    )
                }
            }

            Spacer(modifier = Modifier.height(NuvioTokens.Space.s12))

            if (selectedDevice != null) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(NuvioTokens.Space.s16))
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f))
                        .border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f), RoundedCornerShape(NuvioTokens.Space.s16))
                        .padding(NuvioTokens.Space.s14),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Tv,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                            )
                        }
                        Spacer(modifier = Modifier.width(NuvioTokens.Space.s12))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = selectedDevice!!.name,
                                fontWeight = FontWeight.SemiBold,
                                color = Color.White,
                                fontSize = 15.sp,
                            )
                            Text(
                                text = "${selectedDevice!!.host}:${selectedDevice!!.port} • Conectado",
                                color = Color.LightGray,
                                fontSize = 12.sp,
                            )
                            if (playbackState.state != CastPlaybackState.IDLE && playbackState.state != CastPlaybackState.STOPPED) {
                                Text(
                                    text = "Estado: ${playbackState.state.name} (${playbackState.positionSeconds}s)",
                                    color = MaterialTheme.colorScheme.primary,
                                    fontSize = 11.sp,
                                )
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(NuvioTokens.Space.s16))
            }

            Text(
                text = "Dispositivos en la misma red Wi-Fi",
                style = MaterialTheme.typography.bodySmall,
                color = Color.Gray,
                modifier = Modifier.padding(bottom = NuvioTokens.Space.s8),
            )

            if (devices.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = NuvioTokens.Space.s16),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "Buscando pantallas Nuvio Legacy...",
                        color = Color.LightGray,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(NuvioTokens.Space.s8),
                    modifier = Modifier.heightIn(max = 240.dp),
                ) {
                    items(devices) { device ->
                        val isCurrent = device.id == selectedDevice?.id
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(NuvioTokens.Space.s12))
                                .background(if (isCurrent) Color.White.copy(alpha = 0.1f) else Color.White.copy(alpha = 0.04f))
                                .clickable {
                                    CastManager.selectDevice(device)
                                    onDismiss()
                                }
                                .padding(NuvioTokens.Space.s12),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Tv,
                                contentDescription = null,
                                tint = if (isCurrent) MaterialTheme.colorScheme.primary else Color.White,
                                modifier = Modifier.size(24.dp),
                            )
                            Spacer(modifier = Modifier.width(NuvioTokens.Space.s12))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = device.name,
                                    color = Color.White,
                                    fontWeight = FontWeight.Medium,
                                )
                                Text(
                                    text = "${device.host}:${device.port}",
                                    color = Color.Gray,
                                    fontSize = 11.sp,
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(NuvioTokens.Space.s12))

            if (!isAddingManual) {
                Button(
                    onClick = { isAddingManual = true },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color.White.copy(alpha = 0.08f),
                        contentColor = Color.White,
                    ),
                    shape = RoundedCornerShape(NuvioTokens.Space.s12),
                ) {
                    Text("Conectar por IP manualmente")
                }
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(NuvioTokens.Space.s12))
                        .background(Color.White.copy(alpha = 0.05f))
                        .padding(NuvioTokens.Space.s12),
                ) {
                    Text(
                        text = "Ingresar IP de Smart TV (ej. 192.168.1.50)",
                        color = Color.LightGray,
                        fontSize = 12.sp,
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    OutlinedTextField(
                        value = manualIp,
                        onValueChange = { manualIp = it },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("192.168.1.X", color = Color.DarkGray) },
                        singleLine = true,
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                    ) {
                        Button(
                            onClick = { isAddingManual = false },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color.Transparent,
                                contentColor = Color.Gray,
                            ),
                        ) {
                            Text("Cancelar")
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(
                            onClick = {
                                if (manualIp.isNotBlank()) {
                                    CastManager.addManualDevice(manualIp.trim())
                                    onDismiss()
                                }
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary,
                            ),
                        ) {
                            Text("Vincular")
                        }
                    }
                }
            }
        }
    }
}
