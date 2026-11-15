package com.eyesec.viewer.ui.player

import android.content.Intent
import android.net.Uri
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AspectRatio
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.FitScreen
import androidx.compose.material.icons.filled.HelpOutline
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.alexvas.rtsp.widget.RtspSurfaceView
import com.eyesec.viewer.data.CameraConfig
import com.eyesec.viewer.data.StreamQuality
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlayerScreen(
    viewModel: PlayerViewModel,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    // Zoom and pan state
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }

    // Auto-hide bottom panel after 7 seconds of inactivity when streaming
    LaunchedEffect(uiState.isControlsVisible, uiState.playerState, uiState.activeModal) {
        if (uiState.isControlsVisible && uiState.playerState == PlayerState.PLAYING && uiState.activeModal == ActiveModal.NONE) {
            delay(7000)
            viewModel.toggleControlsVisibility()
        }
    }

    // Time ticker for CCTV display
    var currentTimeString by remember { mutableStateOf("") }
    LaunchedEffect(Unit) {
        val sdf = SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.getDefault())
        while (true) {
            currentTimeString = sdf.format(Date())
            delay(1000)
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF05070B))
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = {
                        if (uiState.activeModal != ActiveModal.NONE) {
                            viewModel.closeModal()
                        } else {
                            viewModel.toggleControlsVisibility()
                        }
                    },
                    onDoubleTap = {
                        if (scale > 1f) {
                            scale = 1f
                            offset = Offset.Zero
                        } else {
                            scale = 2.5f
                        }
                    }
                )
            }
    ) {
        // Video Player View with Pinch-to-zoom and Pan & Aspect-Ratio / Stretch Mode
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer(
                    scaleX = scale,
                    scaleY = scale,
                    translationX = offset.x,
                    translationY = offset.y
                )
                .pointerInput(Unit) {
                    detectTransformGestures { _, pan, zoom, _ ->
                        val newScale = (scale * zoom).coerceIn(1f, 6f)
                        scale = newScale
                        if (newScale > 1f) {
                            offset = Offset(offset.x + pan.x, offset.y + pan.y)
                        } else {
                            offset = Offset.Zero
                        }
                    }
                },
            contentAlignment = Alignment.Center
        ) {
            val videoModifier = if (uiState.config.stretchToFit) {
                Modifier.fillMaxSize()
            } else {
                val ratio = if (uiState.videoWidth > 0 && uiState.videoHeight > 0) {
                    uiState.videoWidth.toFloat() / uiState.videoHeight.toFloat()
                } else {
                    16f / 9f
                }
                Modifier
                    .fillMaxSize()
                    .aspectRatio(ratio, matchHeightConstraintsFirst = false)
            }

            AndroidView(
                factory = { ctx ->
                    RtspSurfaceView(ctx).apply {
                        layoutParams = FrameLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT
                        )
                        viewModel.attachSurfaceView(this)
                    }
                },
                modifier = videoModifier
            )
        }

        // Live CCTV Top Panel (Preserved exactly as requested)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 14.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier
                    .background(Color(0x990A0E17), RoundedCornerShape(8.dp))
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(
                            when (uiState.playerState) {
                                PlayerState.PLAYING -> Color(0xFF22C55E)
                                PlayerState.CONNECTING -> Color(0xFFEAB308)
                                PlayerState.ERROR -> Color(0xFFEF4444)
                                PlayerState.IDLE -> Color.Gray
                            }
                        )
                )
                Text(
                    text = uiState.config.ip.ifBlank { "EYESEC" },
                    color = Color.White,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace
                )
                if (uiState.pingMs != null) {
                    Text(
                        text = "· ${uiState.pingMs}ms",
                        color = Color(0xFF94A3B8),
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }

            Text(
                text = currentTimeString,
                color = Color(0xFFE2E8F0),
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier
                    .background(Color(0x990A0E17), RoundedCornerShape(8.dp))
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            )
        }

        // Status Overlay (Initial Connect or Error only)
        when (uiState.playerState) {
            PlayerState.CONNECTING -> {
                if (uiState.videoWidth == 0) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = Color(0xDD101723)),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.border(1.dp, Color(0x33FFFFFF), RoundedCornerShape(12.dp))
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 20.dp, vertical = 14.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(20.dp),
                                    color = Color(0xFF38BDF8),
                                    strokeWidth = 2.dp
                                )
                                Text(
                                    text = "Connecting to Camera…",
                                    color = Color.White,
                                    fontSize = 13.sp
                                )
                            }
                        }
                    }
                }
            }
            PlayerState.ERROR -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = Color(0xF0181014)),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .padding(24.dp)
                            .border(1.dp, Color(0x44EF4444), RoundedCornerShape(14.dp))
                    ) {
                        Column(
                            modifier = Modifier.padding(20.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Text(
                                text = "Unable to connect to camera",
                                color = Color(0xFFF87171),
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp
                            )
                            Text(
                                text = uiState.errorMessage ?: "Check IP address, port and RTSP credentials.",
                                color = Color(0xFFCBD5E1),
                                fontSize = 12.sp
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                Button(
                                    onClick = { viewModel.connect() },
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF175C84))
                                ) {
                                    Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text("Retry")
                                }
                                Button(
                                    onClick = { viewModel.openModal(ActiveModal.SETTINGS) },
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF334155))
                                ) {
                                    Icon(Icons.Default.Settings, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text("Settings")
                                }
                            }
                        }
                    }
                }
            }
            PlayerState.IDLE -> {
                if (uiState.config.ip.isBlank()) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Button(
                            onClick = { viewModel.openModal(ActiveModal.SETTINGS) },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF175C84)),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Icon(Icons.Default.Settings, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text("Configure Camera IP & Password")
                        }
                    }
                }
            }
            PlayerState.PLAYING -> {}
        }

        // Floating Containers positioned above the bottom bar
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(bottom = 76.dp)
                .padding(horizontal = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // 1. Settings Floating Container
            AnimatedVisibility(
                visible = uiState.activeModal == ActiveModal.SETTINGS,
                enter = fadeIn() + slideInVertically { it / 2 },
                exit = fadeOut() + slideOutVertically { it / 2 }
            ) {
                FloatingSettingsCard(
                    initialConfig = uiState.config,
                    onClose = { viewModel.closeModal() },
                    onSave = { updated ->
                        viewModel.updateConfig(updated)
                    }
                )
            }

            // 2. Credits Floating Container (fauzan afianto)
            AnimatedVisibility(
                visible = uiState.activeModal == ActiveModal.CREDITS,
                enter = fadeIn() + slideInVertically { it / 2 },
                exit = fadeOut() + slideOutVertically { it / 2 }
            ) {
                FloatingCreditsCard(
                    onClose = { viewModel.closeModal() },
                    onOpenWebsite = {
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://afianto.dev"))
                        context.startActivity(intent)
                    },
                    onOpenEmail = {
                        val intent = Intent(Intent.ACTION_SENDTO).apply {
                            data = Uri.parse("mailto:fauzan@afianto.dev")
                            putExtra(Intent.EXTRA_SUBJECT, "EYESEC Android Viewer")
                        }
                        context.startActivity(intent)
                    }
                )
            }

            // 3. Help Floating Container (How to find EYESEC IP & Password)
            AnimatedVisibility(
                visible = uiState.activeModal == ActiveModal.HELP,
                enter = fadeIn() + slideInVertically { it / 2 },
                exit = fadeOut() + slideOutVertically { it / 2 }
            ) {
                FloatingHelpCard(
                    onClose = { viewModel.closeModal() }
                )
            }
        }

        // Elegant, Floating Bottom Bar: Symmetrical & Uniform (7 uniform 42x42dp actions)
        AnimatedVisibility(
            visible = uiState.isControlsVisible,
            enter = fadeIn() + slideInVertically { it },
            exit = fadeOut() + slideOutVertically { it },
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            Surface(
                color = Color(0xDD0C121D),
                shape = RoundedCornerShape(22.dp),
                shadowElevation = 18.dp,
                modifier = Modifier
                    .padding(horizontal = 12.dp, vertical = 14.dp)
                    .navigationBarsPadding()
                    .border(1.dp, Color(0x33FFFFFF), RoundedCornerShape(22.dp))
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // 1. HIGH Quality Button (Uniform 42x42dp)
                    UniformFloatingButton(
                        label = "HQ",
                        isActive = uiState.config.quality == StreamQuality.MAJOR,
                        onClick = { viewModel.setQuality(StreamQuality.MAJOR) }
                    )

                    // 2. LOW Quality Button (Uniform 42x42dp)
                    UniformFloatingButton(
                        label = "LQ",
                        isActive = uiState.config.quality == StreamQuality.MINOR,
                        onClick = { viewModel.setQuality(StreamQuality.MINOR) }
                    )

                    // 3. Screen Mode Button: Stretch vs Fit (Uniform 42x42dp)
                    UniformFloatingIconButton(
                        icon = if (uiState.config.stretchToFit) Icons.Default.AspectRatio else Icons.Default.FitScreen,
                        isActive = !uiState.config.stretchToFit,
                        contentDescription = if (uiState.config.stretchToFit) "Stretch to Fill" else "Fit Aspect Ratio",
                        onClick = { viewModel.toggleStretchMode() }
                    )

                    // 4. Config / Settings Button (Uniform 42x42dp)
                    UniformFloatingIconButton(
                        icon = Icons.Default.Settings,
                        isActive = uiState.activeModal == ActiveModal.SETTINGS,
                        contentDescription = "Config",
                        onClick = { viewModel.openModal(ActiveModal.SETTINGS) }
                    )

                    // 5. Help Button (Uniform 42x42dp)
                    UniformFloatingIconButton(
                        icon = Icons.Default.HelpOutline,
                        isActive = uiState.activeModal == ActiveModal.HELP,
                        contentDescription = "Help Guide",
                        onClick = { viewModel.openModal(ActiveModal.HELP) }
                    )

                    // 6. Credits Button (Uniform 42x42dp)
                    UniformFloatingIconButton(
                        icon = Icons.Default.Person,
                        isActive = uiState.activeModal == ActiveModal.CREDITS,
                        contentDescription = "Credits",
                        onClick = { viewModel.openModal(ActiveModal.CREDITS) }
                    )

                    // 7. Reconnect Button (Uniform 42x42dp)
                    UniformFloatingIconButton(
                        icon = Icons.Default.Refresh,
                        isActive = false,
                        contentDescription = "Reconnect",
                        onClick = { viewModel.connect() }
                    )
                }
            }
        }
    }
}

@Composable
private fun UniformFloatingButton(
    label: String,
    isActive: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(42.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(
                if (isActive) Color(0xFF175C84) else Color(0x331E293B)
            )
            .border(
                1.dp,
                if (isActive) Color(0xFF38BDF8) else Color(0x1FFFFFFF),
                RoundedCornerShape(12.dp)
            )
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            color = if (isActive) Color.White else Color(0xFF94A3B8)
        )
    }
}

@Composable
private fun UniformFloatingIconButton(
    icon: ImageVector,
    isActive: Boolean,
    contentDescription: String,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(42.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(
                if (isActive) Color(0xFF175C84) else Color(0x331E293B)
            )
            .border(
                1.dp,
                if (isActive) Color(0xFF38BDF8) else Color(0x1FFFFFFF),
                RoundedCornerShape(12.dp)
            )
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = if (isActive) Color(0xFF38BDF8) else Color(0xFFE2E8F0),
            modifier = Modifier.size(19.dp)
        )
    }
}

// -------------------------------------------------------------
// Floating Panel: Settings (IP, Port, Username, Password, Connect)
// -------------------------------------------------------------
@Composable
private fun FloatingSettingsCard(
    initialConfig: CameraConfig,
    onClose: () -> Unit,
    onSave: (CameraConfig) -> Unit
) {
    var ip by remember { mutableStateOf(initialConfig.ip) }
    var port by remember { mutableStateOf(initialConfig.port.toString()) }
    var username by remember { mutableStateOf(initialConfig.username) }
    var password by remember { mutableStateOf(initialConfig.password) }
    var isPasswordVisible by remember { mutableStateOf(false) }

    Surface(
        color = Color(0xF20F172A),
        shape = RoundedCornerShape(20.dp),
        shadowElevation = 20.dp,
        modifier = Modifier
            .widthIn(max = 420.dp)
            .fillMaxWidth()
            .border(1.dp, Color(0x3338BDF8), RoundedCornerShape(20.dp))
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Settings,
                        contentDescription = null,
                        tint = Color(0xFF38BDF8),
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        text = "Camera Connection",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
                IconButton(onClick = onClose, modifier = Modifier.size(28.dp)) {
                    Icon(Icons.Default.Close, contentDescription = "Close", tint = Color(0xFF94A3B8), modifier = Modifier.size(18.dp))
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = ip,
                    onValueChange = { ip = it.trim() },
                    label = { Text("IP Address") },
                    placeholder = { Text("192.168.1.57") },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color(0xFF38BDF8),
                        unfocusedBorderColor = Color(0xFF334155),
                        focusedLabelColor = Color(0xFF38BDF8),
                        unfocusedLabelColor = Color(0xFF94A3B8)
                    ),
                    modifier = Modifier.weight(0.7f)
                )

                OutlinedTextField(
                    value = port,
                    onValueChange = { port = it.filter { ch -> ch.isDigit() } },
                    label = { Text("Port") },
                    placeholder = { Text("554") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color(0xFF38BDF8),
                        unfocusedBorderColor = Color(0xFF334155),
                        focusedLabelColor = Color(0xFF38BDF8),
                        unfocusedLabelColor = Color(0xFF94A3B8)
                    ),
                    modifier = Modifier.weight(0.3f)
                )
            }

            OutlinedTextField(
                value = username,
                onValueChange = { username = it },
                label = { Text("Username") },
                placeholder = { Text("admin") },
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Color(0xFF38BDF8),
                    unfocusedBorderColor = Color(0xFF334155),
                    focusedLabelColor = Color(0xFF38BDF8),
                    unfocusedLabelColor = Color(0xFF94A3B8)
                ),
                modifier = Modifier.fillMaxWidth()
            )

            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                label = { Text("Password") },
                singleLine = true,
                visualTransformation = if (isPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                trailingIcon = {
                    TextButton(onClick = { isPasswordVisible = !isPasswordVisible }) {
                        Text(if (isPasswordVisible) "Hide" else "Show", fontSize = 11.sp, color = Color(0xFF38BDF8))
                    }
                },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Color(0xFF38BDF8),
                    unfocusedBorderColor = Color(0xFF334155),
                    focusedLabelColor = Color(0xFF38BDF8),
                    unfocusedLabelColor = Color(0xFF94A3B8)
                ),
                modifier = Modifier.fillMaxWidth()
            )

            Button(
                onClick = {
                    val parsedPort = port.toIntOrNull() ?: 554
                    onSave(
                        initialConfig.copy(
                            ip = ip,
                            port = parsedPort,
                            username = username.ifBlank { "admin" },
                            password = password
                        )
                    )
                },
                modifier = Modifier.fillMaxWidth().height(44.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF175C84)),
                shape = RoundedCornerShape(10.dp)
            ) {
                Text("Save & Connect", fontWeight = FontWeight.Bold, fontSize = 13.sp)
            }
        }
    }
}

// -------------------------------------------------------------
// Floating Panel: Credits (Fauzan Afianto, afianto.dev)
// -------------------------------------------------------------
@Composable
private fun FloatingCreditsCard(
    onClose: () -> Unit,
    onOpenWebsite: () -> Unit,
    onOpenEmail: () -> Unit
) {
    Surface(
        color = Color(0xF20F172A),
        shape = RoundedCornerShape(20.dp),
        shadowElevation = 20.dp,
        modifier = Modifier
            .widthIn(max = 380.dp)
            .fillMaxWidth()
            .border(1.dp, Color(0x3338BDF8), RoundedCornerShape(20.dp))
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(Brush.linearGradient(listOf(Color(0xFF0284C7), Color(0xFF0369A1)))),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("FA", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }
                    Column {
                        Text("Fauzan Afianto", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                        Text("Developer & Creator", color = Color(0xFF38BDF8), fontSize = 11.sp)
                    }
                }
                IconButton(onClick = onClose, modifier = Modifier.size(28.dp)) {
                    Icon(Icons.Default.Close, contentDescription = "Close", tint = Color(0xFF94A3B8), modifier = Modifier.size(18.dp))
                }
            }

            Text(
                text = "Built with high-performance low-latency hardware decoding for offline local camera surveillance.",
                color = Color(0xFF94A3B8),
                fontSize = 12.sp,
                lineHeight = 17.sp,
                modifier = Modifier.fillMaxWidth()
            )

            HorizontalDivider(color = Color(0x22FFFFFF), thickness = 1.dp)

            // Interactive Link Buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Website
                Button(
                    onClick = onOpenWebsite,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E293B)),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.weight(1f).height(42.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0x3338BDF8))
                ) {
                    Icon(Icons.Default.Language, contentDescription = null, tint = Color(0xFF38BDF8), modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("afianto.dev", color = Color.White, fontSize = 12.sp)
                }

                // Email
                Button(
                    onClick = onOpenEmail,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E293B)),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.weight(1f).height(42.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0x3338BDF8))
                ) {
                    Icon(Icons.Default.Email, contentDescription = null, tint = Color(0xFF38BDF8), modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Email", color = Color.White, fontSize = 12.sp)
                }
            }
        }
    }
}

// -------------------------------------------------------------
// Floating Panel: Help (Finding IP and Password)
// -------------------------------------------------------------
@Composable
private fun FloatingHelpCard(
    onClose: () -> Unit
) {
    Surface(
        color = Color(0xF20F172A),
        shape = RoundedCornerShape(20.dp),
        shadowElevation = 20.dp,
        modifier = Modifier
            .widthIn(max = 400.dp)
            .fillMaxWidth()
            .border(1.dp, Color(0x3338BDF8), RoundedCornerShape(20.dp))
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.HelpOutline,
                        contentDescription = null,
                        tint = Color(0xFF38BDF8),
                        modifier = Modifier.size(22.dp)
                    )
                    Text(
                        text = "Finding Camera Credentials",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
                IconButton(onClick = onClose, modifier = Modifier.size(28.dp)) {
                    Icon(Icons.Default.Close, contentDescription = "Close", tint = Color(0xFF94A3B8), modifier = Modifier.size(18.dp))
                }
            }

            // Step by Step Illustrated Guidance
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                HelpStepItem(
                    stepNumber = "1",
                    title = "Open Camera in EYESEC App",
                    description = "Open your webcam live feed and tap the 3 dots (⋯) in the upper-right corner."
                )

                HelpStepItem(
                    stepNumber = "2",
                    title = "Navigate to ONVIF",
                    description = "In the camera settings page, locate and open the ONVIF section."
                )

                HelpStepItem(
                    stepNumber = "3",
                    title = "Find IP & Password",
                    description = "Your camera's local IP address is displayed here. Use 'Reset Password' to set your local password."
                )
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF172554), RoundedCornerShape(10.dp))
                    .padding(10.dp)
            ) {
                Text(
                    text = "Tip: Default username is 'admin' and default RTSP port is 554.",
                    color = Color(0xFF93C5FD),
                    fontSize = 11.sp,
                    lineHeight = 15.sp
                )
            }
        }
    }
}

@Composable
private fun HelpStepItem(
    stepNumber: String,
    title: String,
    description: String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.Top
    ) {
        Box(
            modifier = Modifier
                .size(22.dp)
                .clip(CircleShape)
                .background(Color(0xFF175C84)),
            contentAlignment = Alignment.Center
        ) {
            Text(stepNumber, color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(title, color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
            Text(description, color = Color(0xFF94A3B8), fontSize = 11.sp, lineHeight = 15.sp)
        }
    }
}
