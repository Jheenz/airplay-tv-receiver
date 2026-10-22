package io.github.jqssun.airplay.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import io.github.jqssun.airplay.R

@Composable
internal fun AirPlayPasscodeDialog(pin: String, onDismiss: () -> Unit) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false,
            dismissOnClickOutside = false
        )
    ) {
        AirPlayPasscodeScreen(pin)
    }
}

@Composable
private fun AirPlayPasscodeScreen(pin: String) {
    BoxWithConstraints(
        Modifier.fillMaxSize().background(Color.Black).safeDrawingPadding(),
        contentAlignment = Alignment.Center
    ) {
        val tileWidth = ((maxWidth - 48.dp - 12.dp * (pin.length - 1)) /
            pin.length.coerceAtLeast(1)).coerceIn(24.dp, 80.dp)
        val pinDescription = stringResource(R.string.airplay_passcode_digits, pin.toList().joinToString(" "))
        Column(
            Modifier.padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                stringResource(R.string.dialog_pin_title), color = Color.White,
                fontSize = 32.sp, fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(16.dp))
            Text(
                stringResource(R.string.airplay_passcode_instruction),
                color = Color.White.copy(alpha = 0.7f), fontSize = 18.sp,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(48.dp))
            Row(
                Modifier.clearAndSetSemantics { contentDescription = pinDescription },
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                pin.forEach { digit ->
                    Box(
                        Modifier.size(tileWidth, tileWidth * 1.6f)
                            .background(Color(0xFF454B5B), RoundedCornerShape(10.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            digit.toString(), color = Color.White,
                            fontSize = (tileWidth.value * 0.8f).sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
        }
    }
}

@Preview(widthDp = 960, heightDp = 540)
@Composable
private fun AirPlayPasscodePreview() {
    AirPlayPasscodeScreen("2871")
}
