// === FILE: app/src/main/java/com/enterwebslinger/network/MainActivity.kt ===
package com.enterwebslinger.network

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.enterwebslinger.network.core.util.configureAppCheck
import com.enterwebslinger.network.ui.components.AiChatModal
import com.enterwebslinger.network.ui.components.CommandPaletteModal
import com.enterwebslinger.network.ui.navigation.AppBottomNavigationBar
import com.enterwebslinger.network.ui.navigation.AppNavHost
import com.enterwebslinger.network.ui.navigation.Screen
import com.enterwebslinger.network.ui.navigation.navigateSingleTopTo
import com.enterwebslinger.network.ui.screens.aaa.AaaIdentityViewModel
import com.enterwebslinger.network.ui.screens.ai.AiAssistantViewModel
import com.enterwebslinger.network.ui.screens.dashboard.DashboardViewModel
import com.enterwebslinger.network.ui.screens.datacenter.DataCenterViewModel
import com.enterwebslinger.network.ui.screens.diagnostics.DiagnosticsViewModel
import com.enterwebslinger.network.ui.screens.engineering.EngineeringViewModel
import com.enterwebslinger.network.ui.screens.enterprise.EnterpriseViewModel
import com.enterwebslinger.network.ui.screens.firewall.FirewallViewModel
import com.enterwebslinger.network.ui.screens.iot.IotApiWorkbenchViewModel
import com.enterwebslinger.network.ui.screens.lanscanner.LanScannerViewModel
import com.enterwebslinger.network.ui.screens.protoolkit.ProToolkitViewModel
import com.enterwebslinger.network.ui.screens.rffield.RfFieldViewModel
import com.enterwebslinger.network.ui.screens.security.SecurityViewModel
import com.enterwebslinger.network.ui.screens.vpn.VpnViewModel
import com.enterwebslinger.network.ui.screens.wifi.WifiSpeedViewModel
import com.enterwebslinger.network.ui.theme.CardBackground
import com.enterwebslinger.network.ui.theme.CyberCyan
import com.enterwebslinger.network.ui.theme.EnterwebSlingerTheme
import com.enterwebslinger.network.ui.theme.StatusCrimson
import com.enterwebslinger.network.ui.theme.TextSecondary
import java.io.PrintWriter
import java.io.StringWriter

class MainActivity : ComponentActivity() {

    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val crashPrefs = getSharedPreferences("enterweb_crash_prefs", Context.MODE_PRIVATE)
        val initialCrashTrace = intent.getStringExtra("EXTRA_CRASH_LOG")
            ?: crashPrefs.getString("last_crash_trace", null)

        val previousHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                val sw = StringWriter()
                val pw = PrintWriter(sw)
                throwable.printStackTrace(pw)
                val trace = sw.toString()
                android.util.Log.e("CrashCatcher", "FATAL CRASH CAUGHT: $trace")

                crashPrefs.edit().putString("last_crash_trace", trace).commit()

                val restartIntent = Intent(applicationContext, MainActivity::class.java).apply {
                    putExtra("EXTRA_CRASH_LOG", trace)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                }
                startActivity(restartIntent)
                Runtime.getRuntime().exit(1)
            } catch (_: Exception) {
                previousHandler?.uncaughtException(thread, throwable)
            }
        }

        configureAppCheck(this, intent)
        enableEdgeToEdge()

        val appContainer = EnterwebSlingerApp.getContainer()

        setContent {
            EnterwebSlingerTheme {
                var activeCrashTrace by rememberSaveable { mutableStateOf(initialCrashTrace) }

                if (!activeCrashTrace.isNullOrBlank()) {
                    DiagnosticCrashScreen(
                        stackTrace = activeCrashTrace!!,
                        onDismiss = {
                            crashPrefs.edit().remove("last_crash_trace").commit()
                            activeCrashTrace = null
                        }
                    )
                } else {
                    val dashboardViewModel: DashboardViewModel = viewModel(factory = appContainer.viewModelFactory)
                    val lanScannerViewModel: LanScannerViewModel = viewModel(factory = appContainer.viewModelFactory)
                    val diagnosticsViewModel: DiagnosticsViewModel = viewModel(factory = appContainer.viewModelFactory)
                    val securityViewModel: SecurityViewModel = viewModel(factory = appContainer.viewModelFactory)
                    val wifiSpeedViewModel: WifiSpeedViewModel = viewModel(factory = appContainer.viewModelFactory)
                    val aiAssistantViewModel: AiAssistantViewModel = viewModel(factory = appContainer.viewModelFactory)
                    val enterpriseViewModel: EnterpriseViewModel = viewModel(factory = appContainer.viewModelFactory)
                    val dataCenterViewModel: DataCenterViewModel = viewModel(factory = appContainer.viewModelFactory)
                    val vpnViewModel: VpnViewModel = viewModel(factory = appContainer.viewModelFactory)
                    val firewallViewModel: FirewallViewModel = viewModel(factory = appContainer.viewModelFactory)
                    val aaaIdentityViewModel: AaaIdentityViewModel = viewModel(factory = appContainer.viewModelFactory)
                    val iotApiViewModel: IotApiWorkbenchViewModel = viewModel(factory = appContainer.viewModelFactory)
                    val engineeringViewModel: EngineeringViewModel = viewModel(factory = appContainer.viewModelFactory)
                    val rfFieldViewModel: RfFieldViewModel = viewModel(factory = appContainer.viewModelFactory)
                    val proToolkitViewModel: ProToolkitViewModel = viewModel(factory = appContainer.viewModelFactory)

                    RequestNetworkPermissions(
                        onPermissionsChecked = {
                            dashboardViewModel.startTelemetry()
                            wifiSpeedViewModel.startTelemetry()
                        }
                    )

                    val navController = rememberNavController()
                    val backStackEntry by navController.currentBackStackEntryAsState()
                    val currentRoute = backStackEntry?.destination?.route ?: Screen.Dashboard.route

                    var showAiChatModal by rememberSaveable { mutableStateOf(false) }
                    var showCommandPalette by rememberSaveable { mutableStateOf(false) }

                    val density = LocalDensity.current
                    val isImeVisible = WindowInsets.ime.getBottom(density) > 0
                    val isTerminalActive = currentRoute == Screen.EnterpriseNoc.route // Or hide when terminal sub-tab active

                    Scaffold(
                        modifier = Modifier
                            .fillMaxSize()
                            .windowInsetsPadding(WindowInsets.safeDrawing),
                        topBar = {
                            TopAppBar(
                                title = {
                                    Text(
                                        text = "Enterweb Slinger",
                                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                        color = CyberCyan
                                    )
                                },
                                actions = {
                                    IconButton(
                                        onClick = { showCommandPalette = true },
                                        modifier = Modifier.testTag("global_command_palette_action")
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Search,
                                            contentDescription = "Command Palette (All Tools)",
                                            tint = CyberCyan
                                        )
                                    }
                                },
                                colors = TopAppBarDefaults.topAppBarColors(containerColor = CardBackground)
                            )
                        },
                        floatingActionButton = {
                            if (!isImeVisible && !isTerminalActive) {
                                FloatingActionButton(
                                    onClick = { showAiChatModal = true },
                                    containerColor = CyberCyan,
                                    contentColor = Color(0xFF00363D),
                                    modifier = Modifier
                                        .testTag("global_ai_chat_fab")
                                        .padding(bottom = if (isImeVisible) 0.dp else 56.dp)
                                ) {
                                    Icon(imageVector = Icons.Default.AutoAwesome, contentDescription = "AI Diagnostics Assistant")
                                }
                            }
                        },
                        bottomBar = {
                            if (!isImeVisible) {
                                AppBottomNavigationBar(
                                    currentRoute = currentRoute,
                                    onNavigate = { route -> navController.navigateSingleTopTo(route) }
                                )
                            }
                        }
                    ) { innerPadding ->
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(innerPadding)
                        ) {
                            AppNavHost(
                                navController = navController,
                                dashboardViewModel = dashboardViewModel,
                                lanScannerViewModel = lanScannerViewModel,
                                diagnosticsViewModel = diagnosticsViewModel,
                                securityViewModel = securityViewModel,
                                wifiSpeedViewModel = wifiSpeedViewModel,
                                aiAssistantViewModel = aiAssistantViewModel,
                                enterpriseViewModel = enterpriseViewModel,
                                dataCenterViewModel = dataCenterViewModel,
                                vpnViewModel = vpnViewModel,
                                firewallViewModel = firewallViewModel,
                                aaaIdentityViewModel = aaaIdentityViewModel,
                                iotApiViewModel = iotApiViewModel,
                                engineeringViewModel = engineeringViewModel,
                                rfFieldViewModel = rfFieldViewModel,
                                proToolkitViewModel = proToolkitViewModel,
                                onOpenAiChat = { showAiChatModal = true }
                            )

                            if (showAiChatModal) {
                                AiChatModal(
                                    viewModel = aiAssistantViewModel,
                                    onDismiss = { showAiChatModal = false }
                                )
                            }

                            if (showCommandPalette) {
                                CommandPaletteModal(
                                    onDismiss = { showCommandPalette = false },
                                    onSelectTool = { tool ->
                                        navController.navigateSingleTopTo(tool.route)
                                        tool.tabIndex?.let { tabIdx ->
                                            when (tool.route) {
                                                Screen.EngineeringUtils.route -> engineeringViewModel.setSelectedTab(tabIdx)
                                                Screen.EnterpriseNoc.route -> enterpriseViewModel.setSelectedTab(tabIdx)
                                                Screen.DataCenter.route -> {
                                                    val mappedSection = when (tabIdx) {
                                                        0, 1 -> 0
                                                        2, 3 -> 1
                                                        4 -> 2
                                                        else -> tabIdx.coerceIn(0, 2)
                                                    }
                                                    dataCenterViewModel.setSelectedSection(mappedSection)
                                                }
                                                Screen.VpnWorkbench.route -> vpnViewModel.setSelectedTab(tabIdx)
                                                Screen.FirewallHub.route -> firewallViewModel.setSelectedTab(tabIdx)
                                                Screen.AaaIdentity.route -> aaaIdentityViewModel.setSelectedTab(tabIdx)
                                                Screen.IotApiWorkbench.route -> iotApiViewModel.setSelectedTab(tabIdx)
                                                Screen.Diagnostics.route -> diagnosticsViewModel.setSelectedTab(tabIdx)
                                                Screen.Security.route -> securityViewModel.setSelectedTab(tabIdx)
                                                Screen.WifiSpeed.route -> wifiSpeedViewModel.setSelectedTab(tabIdx)
                                                Screen.RfField.route -> rfFieldViewModel.setSelectedTab(tabIdx)
                                                Screen.ProToolkit.route -> proToolkitViewModel.setSelectedTab(tabIdx)
                                            }
                                        }
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DiagnosticCrashScreen(
    stackTrace: String,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val scrollState = rememberScrollState()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0A0F1D))
            .padding(16.dp)
            .windowInsetsPadding(WindowInsets.safeDrawing)
    ) {
        Column(
            modifier = Modifier.fillMaxSize()
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.BugReport,
                    contentDescription = "Crash Detected",
                    tint = StatusCrimson,
                    modifier = Modifier.size(36.dp)
                )
                Column {
                    Text(
                        text = "DIAGNOSTIC CRASH DETECTED",
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontWeight = FontWeight.Bold,
                            color = StatusCrimson
                        )
                    )
                    Text(
                        text = "An uncaught runtime exception occurred. Stack trace captured:",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .background(Color(0xFF030712), RoundedCornerShape(8.dp))
                    .border(1.dp, Color(0xFF1F2937), RoundedCornerShape(8.dp))
                    .padding(12.dp)
            ) {
                Text(
                    text = stackTrace,
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                        lineHeight = 16.sp,
                        color = Color(0xFFF87171)
                    ),
                    modifier = Modifier.verticalScroll(scrollState)
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedButton(
                    onClick = {
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        val clip = ClipData.newPlainText("Crash Stack Trace", stackTrace)
                        clipboard.setPrimaryClip(clip)
                        Toast.makeText(context, "Stack trace copied to clipboard", Toast.LENGTH_SHORT).show()
                    },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = CyberCyan)
                ) {
                    Icon(imageVector = Icons.Default.ContentCopy, contentDescription = "Copy")
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Copy Stack Trace")
                }

                Button(
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = CyberCyan, contentColor = Color(0xFF00363D))
                ) {
                    Icon(imageVector = Icons.Default.Refresh, contentDescription = "Restart")
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Restart App")
                }
            }
        }
    }
}

@Composable
private fun RequestNetworkPermissions(onPermissionsChecked: () -> Unit) {
    val context = LocalContext.current
    val permissionsToRequest = buildList {
        add(Manifest.permission.ACCESS_FINE_LOCATION)
        add(Manifest.permission.ACCESS_COARSE_LOCATION)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            add(Manifest.permission.NEARBY_WIFI_DEVICES)
        }
    }

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { _ ->
        onPermissionsChecked()
    }

    LaunchedEffect(Unit) {
        val fineLocationGranted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        val nearbyGranted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.NEARBY_WIFI_DEVICES
            ) == PackageManager.PERMISSION_GRANTED
        } else true

        if (fineLocationGranted && nearbyGranted) {
            onPermissionsChecked()
        } else {
            launcher.launch(permissionsToRequest.toTypedArray())
        }
    }
}
