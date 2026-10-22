package br.italo.italock.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import br.italo.italock.ui.theme.ButtonShape
import br.italo.italock.R
import androidx.compose.ui.res.stringResource
import kotlinx.coroutines.delay

private val DialogRed = Color(0xFFB71C1C)

/** Diálogo do modo SEM CONFIRMAÇÃO: vermelho, caixa "Entendo o risco" e botão Ligar só depois de 5 s. */
@Composable
fun DangerDialog(name: String, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    var understood by remember { mutableStateOf(false) }
    var secondsLeft by remember { mutableIntStateOf(5) }
    LaunchedEffect(name) { while (secondsLeft > 0) { delay(1000); secondsLeft-- } }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = DialogRed,
        iconContentColor = Color.White,
        titleContentColor = Color.White,
        textContentColor = Color.White,
        icon = { Icon(Icons.Outlined.Warning, contentDescription = null, modifier = Modifier.size(36.dp)) },
        title = { Text(stringResource(R.string.danger_title), fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(R.string.danger_heading, name), fontWeight = FontWeight.SemiBold)
                Text(stringResource(R.string.danger_text))
                Text(
                    stringResource(R.string.danger_scope),
                    style = MaterialTheme.typography.bodySmall,
                )
                Row(
                    Modifier.heightIn(min = 48.dp).toggleable(understood, role = Role.Checkbox) { understood = it },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Checkbox(
                        checked = understood, onCheckedChange = null,
                        colors = CheckboxDefaults.colors(checkedColor = Color.White, uncheckedColor = Color.White, checkmarkColor = DialogRed),
                    )
                    Text(stringResource(R.string.danger_understood), fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(start = 8.dp))
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                enabled = understood && secondsLeft == 0,
                shape = ButtonShape,
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color.White, contentColor = DialogRed,
                    disabledContainerColor = Color.White.copy(alpha = 0.3f), disabledContentColor = Color.White.copy(alpha = 0.8f),
                ),
            ) { Text(if (secondsLeft > 0) stringResource(R.string.danger_turn_on_wait, secondsLeft) else stringResource(R.string.common_turn_on), fontWeight = FontWeight.SemiBold) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, colors = ButtonDefaults.textButtonColors(contentColor = Color.White)) { Text(stringResource(R.string.common_cancel)) }
        },
    )
}
