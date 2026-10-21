package io.github.jqssun.airplay.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Airplay
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.jqssun.airplay.R

private val LocalSettingHelp = staticCompositionLocalOf<(String, String) -> Unit> { { _, _ -> } }

@Composable
internal fun TvAdvancedSettingsFrame(onBack: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    val defaultTitle = stringResource(R.string.airplay_advanced)
    val defaultHelp = stringResource(R.string.tv_settings_help)
    var help by remember { mutableStateOf(defaultTitle to defaultHelp) }
    val firstFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { firstFocus.requestFocusUntilLanded() }
    CompositionLocalProvider(LocalSettingHelp provides { title, description -> help = title to description }) {
        AirPlayColumns(title = defaultTitle, illustration = {
            Column(Modifier.fillMaxSize().padding(horizontal = 12.dp, vertical = 32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Box(Modifier.size(128.dp).background(AirPlayPanel, RoundedCornerShape(28.dp)), contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.Airplay, null, tint = Color.White, modifier = Modifier.size(64.dp))
                }
                Text(help.first, fontSize = 22.sp, fontWeight = FontWeight.Medium, color = Color.White,
                    textAlign = TextAlign.Center, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 24.dp))
                Text(help.second, fontSize = 17.sp, color = Color(0xFFB9BBBB), textAlign = TextAlign.Center,
                    maxLines = 6, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 12.dp))
            }
        }) {
            AirPlayMenuRow(stringResource(R.string.back_airplay_settings), onClick = onBack,
                modifier = Modifier.focusRequester(firstFocus))
            content()
        }
    }
}

@Composable
internal fun TvSettingRow(title: String, value: String, description: String?, onClick: () -> Unit) {
    val onHelp = LocalSettingHelp.current
    AirPlayMenuRow(title, value, onClick,
        modifier = Modifier.onFocusChanged { if (it.isFocused) onHelp(title, description.orEmpty()) })
}

@Composable
internal fun TvTextSetting(
    title: String, value: String, description: String?, range: IntRange?, onCommit: (String) -> Unit
) {
    var editing by remember { mutableStateOf(false) }
    TvSettingRow(title, value, description, onClick = { editing = true })
    if (editing) TvTextSettingDialog(title, value, description, range,
        onDismiss = { editing = false }, onSave = { onCommit(it); editing = false })
}

@Composable
internal fun TvTextSettingDialog(
    title: String,
    value: String,
    description: String? = null,
    range: IntRange? = null,
    validator: (String) -> Boolean = { it.isNotBlank() },
    onDismiss: () -> Unit,
    onSave: (String) -> Unit
) {
    var text by remember(value) { mutableStateOf(value) }
    val valid = validator(text) && (range == null || text.toIntOrNull()?.let { it in range } == true)
    val fieldFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { fieldFocus.requestFocusUntilLanded() }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (!description.isNullOrEmpty()) Text(description)
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = if (range == null) it else it.filter(Char::isDigit).take(6) },
                    modifier = Modifier.fillMaxWidth().focusRequester(fieldFocus),
                    singleLine = true, isError = !valid,
                    supportingText = if (range != null) { { Text("${range.first} – ${range.last}") } } else null,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = if (range == null) KeyboardType.Text else KeyboardType.Number, imeAction = ImeAction.Done
                    ),
                    keyboardActions = KeyboardActions(onDone = { if (valid) onSave(text.trim()) })
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(text.trim()) }, enabled = valid, modifier = Modifier.heightIn(min = 48.dp).dpadFocus()) {
                Text(stringResource(R.string.tv_save), fontSize = 18.sp)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss, modifier = Modifier.heightIn(min = 48.dp).dpadFocus()) { Text(stringResource(R.string.tv_cancel), fontSize = 18.sp) } }
    )
}

@Composable
internal fun <T> TvChoiceSetting(
    title: String,
    description: String,
    value: T,
    options: List<Pair<T, String>>,
    customValidator: ((String) -> Boolean)? = null,
    onCustomValueChange: ((String) -> Unit)? = null,
    onValueChange: (T) -> Unit
) {
    var choosing by remember { mutableStateOf(false) }
    var custom by remember { mutableStateOf(false) }
    TvSettingRow(title, options.firstOrNull { it.first == value }?.second ?: value.toString(), description,
        onClick = { choosing = true })
    if (choosing) {
        val selectedFocus = remember { FocusRequester() }
        val selectedIndex = options.indexOfFirst { it.first == value }.coerceAtLeast(0)
        LaunchedEffect(Unit) { selectedFocus.requestFocusUntilLanded() }
        AlertDialog(
            onDismissRequest = { choosing = false },
            title = { Text(title) },
            text = {
                Column(Modifier.heightIn(max = 280.dp).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    options.forEachIndexed { index, (key, label) ->
                        AirPlayMenuRow(label, if (key == value) stringResource(R.string.tv_selected) else null,
                            onClick = { onValueChange(key); choosing = false },
                            modifier = if (index == selectedIndex) Modifier.focusRequester(selectedFocus) else Modifier,
                            chevron = false)
                    }
                    if (customValidator != null && onCustomValueChange != null) AirPlayMenuRow(stringResource(R.string.chip_custom),
                        onClick = { choosing = false; custom = true })
                }
            },
            confirmButton = { TextButton(onClick = { choosing = false }, modifier = Modifier.heightIn(min = 48.dp).dpadFocus()) { Text(stringResource(R.string.tv_cancel), fontSize = 18.sp) } }
        )
    }
    // Custom values are used only by the string-based resolution and frame-rate settings.
    if (custom && customValidator != null && onCustomValueChange != null) TvTextSettingDialog(
        title, value.toString(), description, validator = customValidator,
        onDismiss = { custom = false },
        onSave = {
            onCustomValueChange(it)
            custom = false
        }
    )
}
