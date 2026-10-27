package com.joseleandro.scaniqo.ui.documentCommon.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import com.joseleandro.scaniqo.R

@Composable
internal fun DocumentPageJumpDialog(
    pageCount: Int,
    onDismiss: () -> Unit,
    onPage: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    var value by rememberSaveable { mutableStateOf("") }
    val page = value.map { Character.digit(it, 10) }.joinToString("").toIntOrNull()
    val valid = page != null && page in 1..pageCount
    fun go() { if (valid) { onPage(requireNotNull(page) - 1); onDismiss() } }
    AlertDialog(
        onDismissRequest = onDismiss, modifier = modifier,
        title = { Text(stringResource(R.string.pages_v2_go_to_page)) },
        text = {
            OutlinedTextField(
                value = value,
                onValueChange = { next ->
                    if (next.length <= pageCount.toString().length && next.all { Character.digit(it, 10) >= 0 })
                        value = next
                },
                modifier = Modifier.fillMaxWidth(), singleLine = true,
                label = { Text(stringResource(R.string.utility_pages)) },
                supportingText = { Text(stringResource(R.string.pages_v2_jump_hint, pageCount)) },
                isError = value.isNotEmpty() && !valid,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Go),
                keyboardActions = KeyboardActions(onGo = { go() }),
            )
        },
        confirmButton = {
            TextButton(onClick = ::go, enabled = valid) { Text(stringResource(R.string.pages_v2_go_to_page)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(android.R.string.cancel)) }
        },
    )
}
