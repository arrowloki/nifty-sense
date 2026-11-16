package com.niftysense.app.presentation.settings

import android.graphics.Bitmap
import android.net.Uri
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.HelpOutline
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.LightMode
import androidx.compose.material.icons.rounded.PictureInPictureAlt
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.SettingsBrightness
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.material.icons.rounded.Transform
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.niftysense.app.data.remote.FyersWebSocketStatus
import com.niftysense.app.presentation.theme.Emerald500
import com.niftysense.app.presentation.theme.Rose500

@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val currentTheme by viewModel.themeMode.collectAsState()
    val refreshInterval by viewModel.refreshInterval.collectAsState()

    val savedAppId by viewModel.fyersAppId.collectAsState()
    val savedToken by viewModel.fyersAccessToken.collectAsState()
    val savedSecretKey by viewModel.fyersSecretKey.collectAsState()
    val savedRedirectUri by viewModel.fyersRedirectUri.collectAsState()
    val testState by viewModel.fyersTestState.collectAsState()
    val wsState by viewModel.fyersWebSocketState.collectAsState()

    var inputAppId by remember(savedAppId) { mutableStateOf(savedAppId ?: "") }
    var inputToken by remember(savedToken) { mutableStateOf(savedToken ?: "") }
    var inputSecretKey by remember(savedSecretKey) { mutableStateOf(savedSecretKey ?: "") }
    var isTokenVisible by remember { mutableStateOf(false) }
    var isSecretVisible by remember { mutableStateOf(false) }

    var showOAuthDialog by remember { mutableStateOf(false) }
    var showAuthCodeConverter by remember { mutableStateOf(false) }
    var showStepByStepGuide by remember { mutableStateOf(false) }
    var authCodeOrUrlInput by remember { mutableStateOf("") }

    // In-App Fyers OAuth Login Dialog
    if (showOAuthDialog) {
        FyersOAuthLoginDialog(
            appId = inputAppId,
            redirectUri = savedRedirectUri,
            onDismiss = { showOAuthDialog = false },
            onAuthCodeReceived = { authCode ->
                showOAuthDialog = false
                viewModel.exchangeAndSaveToken(
                    appId = inputAppId,
                    secretKey = inputSecretKey,
                    authCodeOrUrl = authCode
                )
            }
        )
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onNavigateBack) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                        contentDescription = "Back",
                        tint = MaterialTheme.colorScheme.onBackground
                    )
                }
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "Settings",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground
                )
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 10.dp)
        ) {
            // ==============================================================
            // --- SECTION 1: FYERS LIVE STREAMING INTEGRATION ---
            // ==============================================================
            val isConnected = !savedToken.isNullOrEmpty()
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "FYERS REAL-TIME BROKER STREAMING",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    letterSpacing = 1.sp
                )

                // Status Pill
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(
                            if (isConnected) Emerald500.copy(alpha = 0.15f)
                            else MaterialTheme.colorScheme.surfaceVariant
                        )
                        .padding(horizontal = 8.dp, vertical = 3.dp)
                ) {
                    Text(
                        text = if (isConnected) "FYERS BROKER ACTIVE" else "PUBLIC FEED (NORMAL)",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isConnected) Emerald500 else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Spacer(modifier = Modifier.height(8.dp))

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = CardDefaults.outlinedCardBorder().copy(
                    brush = androidx.compose.ui.graphics.SolidColor(
                        if (isConnected) Emerald500.copy(alpha = 0.4f)
                        else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                    )
                )
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Official Fyers Market Data Feed",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )

                        // WebSocket Live Status Badge
                        if (!savedToken.isNullOrBlank()) {
                            val (wsColor, wsLabel) = when (wsState.status) {
                                FyersWebSocketStatus.STREAMING -> Pair(Emerald500, "WebSocket: Streaming")
                                FyersWebSocketStatus.CONNECTING, FyersWebSocketStatus.AUTHENTICATING, FyersWebSocketStatus.SUBSCRIBED -> Pair(MaterialTheme.colorScheme.primary, "WebSocket: Connecting")
                                FyersWebSocketStatus.ERROR -> Pair(Rose500, "WebSocket: Fallback")
                                FyersWebSocketStatus.DISCONNECTED -> Pair(MaterialTheme.colorScheme.outline, "WebSocket: Idle")
                            }

                            Row(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(wsColor.copy(alpha = 0.12f))
                                    .padding(horizontal = 8.dp, vertical = 3.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(6.dp)
                                        .clip(CircleShape)
                                        .background(wsColor)
                                )
                                Spacer(modifier = Modifier.width(5.dp))
                                Text(
                                    text = wsLabel,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = wsColor
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Connect your Fyers account to stream live sub-second broker quotes via Data WebSocket & REST quotes. 100% Free Plan (2 symbols • zero API charges).",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        lineHeight = 16.sp
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    // ==============================================================
                    // --- 1-TAP LOGIN & MOBILE TOKEN TOOLBAR ---
                    // ==============================================================
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = {
                                if (inputAppId.isBlank()) {
                                    Toast.makeText(context, "Please enter your Fyers App ID first (e.g. XC12345-100)", Toast.LENGTH_SHORT).show()
                                } else if (inputSecretKey.isBlank()) {
                                    Toast.makeText(context, "Please enter your App Secret Key below for 1-Tap Login", Toast.LENGTH_SHORT).show()
                                } else {
                                    showOAuthDialog = true
                                }
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primaryContainer,
                                contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                            ),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.weight(1.2f)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Bolt,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(5.dp))
                            Text("⚡ 1-Tap Login", fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
                        }

                        OutlinedButton(
                            onClick = { showAuthCodeConverter = !showAuthCodeConverter },
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Transform,
                                contentDescription = null,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Convert Code", fontSize = 11.sp)
                        }

                        IconButton(
                            onClick = { showStepByStepGuide = !showStepByStepGuide },
                            modifier = Modifier.size(40.dp)
                        ) {
                            Icon(
                                imageVector = if (showStepByStepGuide) Icons.Rounded.ExpandLess else Icons.Rounded.HelpOutline,
                                contentDescription = "Setup Guide",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }

                    // --- EXPANDABLE: Step-by-Step Setup Guide ---
                    AnimatedVisibility(
                        visible = showStepByStepGuide,
                        enter = fadeIn() + expandVertically(),
                        exit = fadeOut() + shrinkVertically()
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 10.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                                .padding(12.dp)
                        ) {
                            Text(
                                text = "📖 How to connect Fyers on Mobile (100% Free):",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "1. Visit myapi.fyers.in & log in with your Fyers credentials.\n" +
                                        "2. Create App: Set App Name to 'NiftySense' and Redirect URI to 'https://trade.fyers.in/api-login/redirect-uri/index.html'.\n" +
                                        "3. Copy your App ID (e.g. XC12345-100) and Secret Key into the fields below.\n" +
                                        "4. Tap '⚡ 1-Tap Login' above: Log in with Mobile/OTP/PIN once, and NiftySense automatically generates your daily Access Token and connects live streaming!\n\n" +
                                        "💡 Why did your token show 'invalid'? Fyers' web login URL provides a temporary 'auth_code' (valid 2 mins), not an Access Token. Our 1-Tap Login or 'Convert Code' button converts it automatically!",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                lineHeight = 16.sp
                            )
                        }
                    }

                    // --- EXPANDABLE: Convert Auth Code / URL Tool ---
                    AnimatedVisibility(
                        visible = showAuthCodeConverter,
                        enter = fadeIn() + expandVertically(),
                        exit = fadeOut() + shrinkVertically()
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 10.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.05f))
                                .padding(12.dp)
                        ) {
                            Text(
                                text = "🔄 Convert Auth Code or Browser URL to Access Token:",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Paste either the full browser address bar URL (e.g. https://trade.fyers.in/...auth_code=eyJ...) or the raw auth_code string here:",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(8.dp))

                            OutlinedTextField(
                                value = authCodeOrUrlInput,
                                onValueChange = { authCodeOrUrlInput = it },
                                label = { Text("Paste Auth Code or Redirect URL", fontSize = 11.5.sp) },
                                placeholder = { Text("https://trade.fyers.in/...auth_code=eyJ...", fontSize = 11.sp) },
                                singleLine = false,
                                maxLines = 3,
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(8.dp)
                            )

                            Spacer(modifier = Modifier.height(8.dp))

                            Button(
                                onClick = {
                                    if (inputAppId.isBlank()) {
                                        Toast.makeText(context, "Please enter your App ID first", Toast.LENGTH_SHORT).show()
                                    } else if (inputSecretKey.isBlank()) {
                                        Toast.makeText(context, "Please enter your App Secret Key below", Toast.LENGTH_SHORT).show()
                                    } else if (authCodeOrUrlInput.isBlank()) {
                                        Toast.makeText(context, "Please paste the Auth Code or URL first", Toast.LENGTH_SHORT).show()
                                    } else {
                                        viewModel.exchangeAndSaveToken(
                                            appId = inputAppId,
                                            secretKey = inputSecretKey,
                                            authCodeOrUrl = authCodeOrUrlInput
                                        )
                                    }
                                },
                                enabled = authCodeOrUrlInput.isNotBlank() && !testState.isLoading,
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("Convert & Connect Live Feed", fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // App ID input
                    OutlinedTextField(
                        value = inputAppId,
                        onValueChange = { inputAppId = it },
                        label = { Text("Fyers App ID (e.g. XC12345-100)", fontSize = 12.sp) },
                        placeholder = { Text("e.g. XC12345-100 (from Fyers API Dashboard)", fontSize = 12.sp) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)
                        )
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    // Secret Key input (optional, enables 1-tap re-login)
                    OutlinedTextField(
                        value = inputSecretKey,
                        onValueChange = {
                            inputSecretKey = it
                            viewModel.saveFyersSecretKey(it)
                        },
                        label = { Text("Fyers App Secret Key (for 1-Tap Login)", fontSize = 12.sp) },
                        placeholder = { Text("Secret Key from myapi.fyers.in dashboard", fontSize = 12.sp) },
                        singleLine = true,
                        visualTransformation = if (isSecretVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        trailingIcon = {
                            IconButton(onClick = { isSecretVisible = !isSecretVisible }) {
                                Icon(
                                    imageVector = if (isSecretVisible) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                                    contentDescription = if (isSecretVisible) "Hide secret" else "Show secret",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)
                        )
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    // Access Token input
                    OutlinedTextField(
                        value = inputToken,
                        onValueChange = { inputToken = it },
                        label = { Text("Daily Access Token (or APP_ID:TOKEN)", fontSize = 12.sp) },
                        placeholder = { Text("Auto-filled via 1-Tap Login, or paste here", fontSize = 12.sp) },
                        singleLine = true,
                        visualTransformation = if (isTokenVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        trailingIcon = {
                            IconButton(onClick = { isTokenVisible = !isTokenVisible }) {
                                Icon(
                                    imageVector = if (isTokenVisible) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                                    contentDescription = if (isTokenVisible) "Hide token" else "Show token",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)
                        )
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    // Action Buttons (Save & Apply / Test / Disconnect)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = {
                                viewModel.saveFyersCredentials(
                                    appId = inputAppId,
                                    token = inputToken,
                                    secretKey = inputSecretKey,
                                    enabled = true
                                )
                            },
                            enabled = inputToken.isNotBlank() && !testState.isLoading,
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.weight(1.1f)
                        ) {
                            if (testState.isLoading) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(14.dp),
                                    strokeWidth = 2.dp,
                                    color = MaterialTheme.colorScheme.onPrimary
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Connecting…", fontSize = 11.5.sp)
                            } else {
                                Icon(
                                    imageVector = Icons.Rounded.Bolt,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(5.dp))
                                Text("Save & Apply", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        }

                        OutlinedButton(
                            onClick = {
                                viewModel.testFyersConnection(inputAppId, inputToken)
                            },
                            enabled = inputToken.isNotBlank() && !testState.isLoading,
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.weight(0.9f)
                        ) {
                            if (testState.isLoading) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(14.dp),
                                    strokeWidth = 2.dp,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Testing…", fontSize = 11.sp)
                            } else {
                                Icon(
                                    imageVector = Icons.Rounded.Sync,
                                    contentDescription = null,
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Test Token", fontSize = 11.5.sp)
                            }
                        }

                        if (!savedToken.isNullOrBlank()) {
                            OutlinedButton(
                                onClick = {
                                    viewModel.clearFyersCredentials()
                                    inputToken = ""
                                    inputAppId = ""
                                    inputSecretKey = ""
                                    Toast.makeText(context, "Fyers disconnected. Reverted to normal public feed.", Toast.LENGTH_SHORT).show()
                                },
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Text("Disconnect", fontSize = 11.sp)
                            }
                        }
                    }

                    // Test Result Feedback Banner
                    testState.message?.let { msg ->
                        Spacer(modifier = Modifier.height(10.dp))
                        val isSuccess = testState.isSuccess == true
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(
                                    if (isSuccess) Emerald500.copy(alpha = 0.12f)
                                    else Rose500.copy(alpha = 0.12f)
                                )
                                .padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = if (isSuccess) Icons.Rounded.CheckCircle else Icons.Rounded.ErrorOutline,
                                contentDescription = null,
                                tint = if (isSuccess) Emerald500 else Rose500,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = msg,
                                fontSize = 11.5.sp,
                                color = if (isSuccess) Emerald500 else Rose500,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Privacy & Security Notice
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Security,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Token stored securely in private local device storage. Never sent to any 3rd party.",
                            fontSize = 10.5.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // ==============================================================
            // --- SECTION 2: WIDGET & BACKGROUND SYNC ---
            // ==============================================================
            Text(
                text = "WIDGET & BACKGROUND SYNC",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                letterSpacing = 1.sp
            )
            Spacer(modifier = Modifier.height(8.dp))

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Periodic Background Refresh",
                        fontSize = 14.5.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Battery-optimized periodic sync via Android WorkManager. Updates widgets in background during trading hours.",
                        fontSize = 11.5.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        lineHeight = 15.sp
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf(15 to "15 min", 30 to "30 min", 60 to "60 min").forEach { (minutes, label) ->
                            val isSelected = refreshInterval == minutes
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(
                                        if (isSelected) MaterialTheme.colorScheme.primary
                                        else MaterialTheme.colorScheme.surfaceVariant
                                    )
                                    .clickable { viewModel.setRefreshInterval(minutes) }
                                    .padding(vertical = 8.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = label,
                                    fontSize = 11.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                    color = if (isSelected) MaterialTheme.colorScheme.onPrimary
                                    else MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                            .padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Sync,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Tip: Tap the ↻ button directly on any home-screen widget to get immediate sub-second market quotes anytime.",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            lineHeight = 15.sp
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(Emerald500.copy(alpha = 0.08f))
                            .padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.PictureInPictureAlt,
                            contentDescription = null,
                            tint = Emerald500,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Groww-Style Floating Ticker: Minimize the app (swipe up/home) or tap the floating window icon to stream live NIFTY & SENSEX over any app in Picture-in-Picture mode.",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurface,
                            lineHeight = 15.sp
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // ==============================================================
            // --- SECTION 3: THEME SELECTOR ---
            // ==============================================================
            Text(
                text = "APPEARANCE & THEME",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                letterSpacing = 1.sp
            )
            Spacer(modifier = Modifier.height(8.dp))

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    ThemeOptionRow(
                        title = "System Default",
                        subtitle = "Follows Android OS dark/light mode",
                        icon = Icons.Rounded.SettingsBrightness,
                        isSelected = currentTheme == "SYSTEM",
                        onClick = { viewModel.setThemeMode("SYSTEM") }
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    ThemeOptionRow(
                        title = "Dark OLED",
                        subtitle = "Deep black slate for low battery usage",
                        icon = Icons.Rounded.DarkMode,
                        isSelected = currentTheme == "DARK",
                        onClick = { viewModel.setThemeMode("DARK") }
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    ThemeOptionRow(
                        title = "Crisp Light",
                        subtitle = "High-contrast clean white surface",
                        icon = Icons.Rounded.LightMode,
                        isSelected = currentTheme == "LIGHT",
                        onClick = { viewModel.setThemeMode("LIGHT") }
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // ==============================================================
            // --- SECTION 4: DATA SOURCE & CACHE ---
            // ==============================================================
            Text(
                text = "DATA SOURCE & CACHE",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                letterSpacing = 1.sp
            )
            Spacer(modifier = Modifier.height(8.dp))

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Smart Switching Engine",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "When Fyers token is present, pulls official real-time ticks. If token expires or is unconfigured, automatically falls back to secondary Moneycontrol & Yahoo Finance feeds.",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        lineHeight = 16.sp
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    OutlinedButton(
                        onClick = {
                            viewModel.clearCache()
                            Toast.makeText(context, "Cache cleared & syncing fresh snapshot", Toast.LENGTH_SHORT).show()
                        },
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.DeleteOutline,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Clear Cache & Sync Fresh Rates", fontSize = 12.sp)
                    }
                }
            }

            Spacer(modifier = Modifier.height(30.dp))
        }
    }
}

/**
 * In-App Full-Screen Dialog hosting Android WebView for Fyers OAuth 2.0 Login.
 * Automatically intercepts the redirect containing the auth_code and hands it to caller.
 */
@Composable
fun FyersOAuthLoginDialog(
    appId: String,
    redirectUri: String,
    onDismiss: () -> Unit,
    onAuthCodeReceived: (authCode: String) -> Unit
) {
    var webViewInstance by remember { mutableStateOf<WebView?>(null) }
    var isLoadingPage by remember { mutableStateOf(true) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Top Bar
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = onDismiss) {
                            Icon(Icons.Rounded.Close, contentDescription = "Close")
                        }
                        Spacer(modifier = Modifier.width(4.dp))
                        Column {
                            Text(
                                text = "Fyers Official Login",
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp,
                                color = MaterialTheme.colorScheme.onBackground
                            )
                            Text(
                                text = "Secure direct login to Fyers servers",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (isLoadingPage) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                        }
                        IconButton(onClick = { webViewInstance?.reload() }) {
                            Icon(Icons.Rounded.Refresh, contentDescription = "Reload")
                        }
                    }
                }

                // WebView
                Box(modifier = Modifier.fillMaxSize()) {
                    AndroidView(
                        factory = { ctx ->
                            WebView(ctx).apply {
                                layoutParams = ViewGroup.LayoutParams(
                                    ViewGroup.LayoutParams.MATCH_PARENT,
                                    ViewGroup.LayoutParams.MATCH_PARENT
                                )
                                settings.javaScriptEnabled = true
                                settings.domStorageEnabled = true
                                settings.useWideViewPort = true
                                settings.loadWithOverviewMode = true
                                settings.userAgentString = "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Mobile Safari/537.36"

                                val cookieManager = CookieManager.getInstance()
                                cookieManager.setAcceptCookie(true)
                                cookieManager.setAcceptThirdPartyCookies(this, true)

                                webViewClient = object : WebViewClient() {
                                    override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                                        val url = request?.url?.toString() ?: return false
                                        return handleInterceptUrl(url)
                                    }

                                    @Deprecated("Deprecated in Java")
                                    override fun shouldOverrideUrlLoading(view: WebView?, url: String?): Boolean {
                                        return handleInterceptUrl(url ?: "")
                                    }

                                    override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                                        super.onPageStarted(view, url, favicon)
                                        isLoadingPage = true
                                        handleInterceptUrl(url ?: "")
                                    }

                                    override fun onPageFinished(view: WebView?, url: String?) {
                                        super.onPageFinished(view, url)
                                        isLoadingPage = false
                                    }

                                    private fun handleInterceptUrl(url: String): Boolean {
                                        if (url.startsWith(redirectUri, ignoreCase = true) || url.contains("auth_code=")) {
                                            val uri = runCatching { Uri.parse(url) }.getOrNull()
                                            val authCode = uri?.getQueryParameter("auth_code")
                                                ?: uri?.getQueryParameter("code")
                                            if (!authCode.isNullOrBlank()) {
                                                onAuthCodeReceived(authCode)
                                                return true
                                            }
                                        }
                                        return false
                                    }
                                }

                                val normalizedAppId = if (!appId.contains("-")) "$appId-100" else appId
                                val encodedRedirect = java.net.URLEncoder.encode(redirectUri, "UTF-8")
                                val loginUrl = "https://api-t1.fyers.in/api/v3/generate-authcode?client_id=$normalizedAppId&redirect_uri=$encodedRedirect&response_type=code&state=niftysense"
                                loadUrl(loginUrl)
                                webViewInstance = this
                            }
                        },
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
        }
    }
}

@Composable
private fun ThemeOptionRow(
    title: String,
    subtitle: String,
    icon: ImageVector,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(
                if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
                else Color.Transparent
            )
            .clickable { onClick() }
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(22.dp)
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                fontSize = 13.5.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = subtitle,
                fontSize = 11.5.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        if (isSelected) {
            Icon(
                imageVector = Icons.Rounded.CheckCircle,
                contentDescription = "Selected",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}
