package io.github.jqssun.airplay.ui

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageManager
import android.location.LocationManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.wifi.WifiInfo
import android.net.wifi.WifiManager
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.location.LocationManagerCompat
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Airplay
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.WifiTethering
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.focusGroup
import io.github.jqssun.airplay.Prefs
import io.github.jqssun.airplay.R
import io.github.jqssun.airplay.appSettingsIntent
import io.github.jqssun.airplay.service.AirPlayService.ServerState
import io.github.jqssun.airplay.viewmodel.LocalNetworkAccess
import io.github.jqssun.airplay.viewmodel.MainViewModel
import kotlinx.coroutines.delay

private val AirPlayBackground = Color.Black
internal val AirPlayPanel = Color(0xFF363838)

@Composable
internal fun AirPlayScreenTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Color.White, onPrimary = Color.Black,
            background = AirPlayBackground, surface = AirPlayBackground,
            surfaceVariant = AirPlayPanel, onSurface = Color.White,
            surfaceContainer = Color(0xFF242626), surfaceContainerHigh = Color(0xFF2E3030),
            surfaceContainerHighest = AirPlayPanel,
            onSurfaceVariant = Color(0xFFB9BBBB)
        ),
        content = content
    )
}

/** A whole row is one remote target; focus uses the white selection from the reference. */
@Composable
internal fun AirPlayMenuRow(
    title: String,
    value: String? = null,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    chevron: Boolean = true
) {
    var focused by remember { mutableStateOf(false) }
    Row(
        modifier.fillMaxWidth().heightIn(min = 52.dp)
            .onFocusChanged { focused = it.isFocused }
            .background(if (focused) Color.White else AirPlayPanel, RoundedCornerShape(8.dp))
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        val foreground = if (focused) Color.Black else Color.White
        Text(title, Modifier.weight(1f), color = foreground, fontSize = 19.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
        if (value != null) Text(value, color = if (focused) Color.DarkGray else Color(0xFFB9BBBB),
            fontSize = 17.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.widthIn(max = 145.dp))
        if (chevron) Icon(Icons.Default.ChevronRight, null, tint = foreground, modifier = Modifier.size(24.dp))
    }
}

@Composable
internal fun AirPlayColumns(
    title: String? = null,
    backgroundColor: Color = AirPlayBackground,
    illustration: @Composable () -> Unit,
    content: @Composable ColumnScope.() -> Unit
) {
    BoxWithConstraints(Modifier.fillMaxSize().background(backgroundColor).safeDrawingPadding()) {
        val wide = maxWidth >= 700.dp
        val padding = if (wide) 40.dp else 24.dp
        Column(Modifier.fillMaxSize().padding(horizontal = padding, vertical = if (wide) 28.dp else 24.dp)) {
            if (title != null) {
                Text(title, Modifier.fillMaxWidth().padding(bottom = 24.dp), textAlign = TextAlign.Center,
                    color = Color.White, fontSize = 30.sp, fontWeight = FontWeight.Medium)
            }
            if (wide) {
                Row(Modifier.fillMaxWidth().weight(1f), verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(48.dp)) {
                    Box(Modifier.weight(0.9f).fillMaxHeight(), contentAlignment = Alignment.Center) { illustration() }
                    Column(
                        Modifier.weight(1.1f).fillMaxHeight().verticalScroll(rememberScrollState()).focusGroup().padding(vertical = 4.dp),
                        verticalArrangement = if (title == null) Arrangement.Center else Arrangement.spacedBy(10.dp),
                    ) {
                        // A single inner column keeps home content centered and menu spacing consistent.
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp), content = content)
                    }
                }
            } else {
                Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
                    illustration()
                    Spacer(Modifier.height(28.dp))
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp), content = content)
                }
            }
        }
    }
}

@Composable
internal fun AirPlayHomeScreen(viewModel: MainViewModel, onSettings: () -> Unit) {
    val serverName by viewModel.pref(Prefs.SERVER_NAME).collectAsState()
    val context = LocalContext.current
    var refreshNetwork by remember { mutableIntStateOf(0) }
    var requestedLocation by remember { mutableStateOf(false) }
    val locationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        requestedLocation = true
        refreshNetwork++
    }
    val network by produceState(initialValue = readNetwork(context), context, refreshNetwork) {
        while (true) {
            value = readNetwork(context)
            delay(5000)
        }
    }
    val settingsFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { settingsFocus.requestFocusUntilLanded() }
    AirPlayColumns(backgroundColor = Color.Black, illustration = {
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            Text("AirPlay", color = Color.White, fontSize = 64.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(24.dp))
            Text(stringResource(R.string.airplay_share), color = Color.White, fontSize = 20.sp,
                textAlign = TextAlign.Center, fontWeight = FontWeight.Medium)
            Row(Modifier.padding(top = 24.dp), horizontalArrangement = Arrangement.spacedBy(28.dp)) {
                Icon(Icons.Default.Airplay, null, tint = Color.White, modifier = Modifier.size(40.dp))
                Icon(Icons.Default.WifiTethering, null, tint = Color.White, modifier = Modifier.size(40.dp))
            }
        }
    }) {
        Column(Modifier.fillMaxWidth().background(AirPlayPanel, RoundedCornerShape(10.dp)).padding(24.dp)) {
            Text(stringResource(R.string.airplay_network), color = Color.White, fontSize = 20.sp)
            Text(stringResource(network.label, *network.args), color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
            HorizontalDivider(Modifier.padding(vertical = 20.dp), color = Color.White.copy(alpha = 0.25f))
            Text(stringResource(R.string.airplay_choose_device), color = Color.White, fontSize = 20.sp)
            Text(serverName, color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
        }
        if (network.label == R.string.airplay_network_wifi) {
            AirPlayMenuRow(stringResource(R.string.airplay_show_network_name), onClick = {
                val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
                val location = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
                val activity = generateSequence(context) { (it as? ContextWrapper)?.baseContext }
                    .filterIsInstance<Activity>().firstOrNull()
                val intent = when {
                    !granted && requestedLocation && activity?.shouldShowRequestPermissionRationale(Manifest.permission.ACCESS_FINE_LOCATION) == false -> context.appSettingsIntent()
                    !granted -> null
                    !LocationManagerCompat.isLocationEnabled(location) -> Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)
                    else -> context.appSettingsIntent()
                }
                if (intent == null) {
                    locationPermission.launch(arrayOf(Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.ACCESS_FINE_LOCATION))
                } else {
                    // Some TV firmware does not expose a dedicated Location page.
                    try { context.startActivity(intent) }
                    catch (_: android.content.ActivityNotFoundException) { context.startActivity(context.appSettingsIntent()) }
                }
            })
            Text(stringResource(R.string.airplay_network_permission_help), color = Color(0xFFB9BBBB), fontSize = 14.sp)
        }
        AirPlayMenuRow(stringResource(R.string.airplay_settings), onClick = onSettings,
            modifier = Modifier.focusRequester(settingsFocus))
        Text(stringResource(R.string.airplay_network_help), color = Color.White.copy(alpha = 0.5f),
            fontSize = 16.sp, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
    }
}

private data class NetworkLabel(val label: Int, val args: Array<out Any> = emptyArray())

@Suppress("DEPRECATION")
private fun readNetwork(context: Context): NetworkLabel = try {
    val connectivity = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    val capabilities = connectivity.getNetworkCapabilities(connectivity.activeNetwork)
    when {
        capabilities == null -> NetworkLabel(R.string.airplay_network_disconnected)
        capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> {
            // NetworkCapabilities obtained synchronously may redact location-sensitive fields,
            // even with permission. Read the caller-filtered WifiManager info as a fallback.
            val transportInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) capabilities.transportInfo as? WifiInfo else null
            fun WifiInfo?.networkName() = this?.ssid?.removeSurrounding("\"")
                ?.takeIf { it.isNotBlank() && it != WifiManager.UNKNOWN_SSID }
            val ssid = transportInfo.networkName()
                ?: (context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager)?.connectionInfo.networkName()
            if (ssid != null) NetworkLabel(R.string.airplay_network_name, arrayOf(ssid))
            else NetworkLabel(R.string.airplay_network_wifi)
        }
        capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> NetworkLabel(R.string.airplay_network_ethernet)
        else -> NetworkLabel(R.string.airplay_network_connected)
    }
} catch (_: SecurityException) {
    NetworkLabel(R.string.airplay_network_unavailable)
}

@Composable
internal fun AirPlaySettingsScreen(
    viewModel: MainViewModel,
    onBack: () -> Unit,
    onAdvanced: () -> Unit,
    onLogs: () -> Unit,
    onLocalNetwork: () -> Unit
) {
    val state by viewModel.serverState.collectAsState()
    val localNetwork by viewModel.localNetwork.collectAsState()
    val requirePin by viewModel.pref(Prefs.REQUIRE_PIN).collectAsState()
    val autoFullscreen by viewModel.pref(Prefs.AUTO_FULLSCREEN).collectAsState()
    val serverName by viewModel.pref(Prefs.SERVER_NAME).collectAsState()
    var editingName by remember { mutableStateOf(false) }
    val firstFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { firstFocus.requestFocusUntilLanded() }
    val needsPermission = state != ServerState.RUNNING && localNetwork != LocalNetworkAccess.GRANTED
    AirPlayColumns(title = stringResource(R.string.airplay_settings), illustration = {
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            Box(Modifier.size(200.dp).background(AirPlayPanel, RoundedCornerShape(44.dp)), contentAlignment = Alignment.Center) {
                Icon(Icons.Default.Airplay, null, tint = Color.White, modifier = Modifier.size(100.dp))
            }
            Text(stringResource(R.string.airplay_settings_help), color = Color(0xFFB9BBBB),
                fontSize = 18.sp, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 28.dp))
        }
    }) {
        AirPlayMenuRow("AirPlay", value = stringResource(when {
            needsPermission -> R.string.btn_allow
            state == ServerState.RUNNING -> R.string.chip_on
            state == ServerState.ERROR -> R.string.error_label
            else -> R.string.chip_off
        }), onClick = {
            if (needsPermission) onLocalNetwork()
            else if (state == ServerState.RUNNING) viewModel.stopServer() else viewModel.startServer()
        }, modifier = Modifier.focusRequester(firstFocus), chevron = false)
        AirPlayMenuRow(stringResource(R.string.setting_require_pin), stringResource(if (requirePin) R.string.chip_on else R.string.chip_off),
            onClick = { viewModel.set(Prefs.REQUIRE_PIN, !requirePin) }, chevron = false)
        AirPlayMenuRow(stringResource(R.string.setting_auto_fullscreen), stringResource(if (autoFullscreen) R.string.chip_on else R.string.chip_off),
            onClick = { viewModel.set(Prefs.AUTO_FULLSCREEN, !autoFullscreen) }, chevron = false)
        AirPlayMenuRow(stringResource(R.string.setting_server_name), serverName, onClick = { editingName = true })
        Spacer(Modifier.height(6.dp))
        AirPlayMenuRow(stringResource(R.string.airplay_advanced), onClick = onAdvanced)
        AirPlayMenuRow(stringResource(R.string.tab_logs), onClick = onLogs)
        AirPlayMenuRow(stringResource(R.string.airplay_back), onClick = onBack)
    }
    if (editingName) TvTextSettingDialog(
        title = stringResource(R.string.setting_server_name), value = serverName,
        onDismiss = { editingName = false },
        onSave = { viewModel.set(Prefs.SERVER_NAME, it); editingName = false }
    )
}
