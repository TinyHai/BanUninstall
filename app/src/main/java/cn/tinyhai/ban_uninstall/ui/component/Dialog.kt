package cn.tinyhai.ban_uninstall.ui.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import cn.tinyhai.ban_uninstall.R
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.basic.TextFieldDefaults
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.window.WindowDialog

interface DialogHandle {
    fun show()
    fun dismiss()
}

interface InputDialogHandle : DialogHandle {
    suspend fun awaitInput(): String
}

interface ConfirmDialogHandle : DialogHandle {
    suspend fun showConfirm(): Boolean
}

interface VerifyPwdDialogHandle : ConfirmDialogHandle {
    suspend fun verify(): Boolean {
        return showConfirm()
    }
}

@Composable
fun rememberConfirmDialog(
    title: String,
    content: String,
): ConfirmDialogHandle {
    val showDialog = rememberSaveable {
        mutableStateOf(false)
    }
    val result = remember {
        Channel<Boolean>()
    }
    val scope = rememberCoroutineScope()
    val handle = remember {
        object : ConfirmDialogHandle {
            override fun show() {
                showDialog.value = true
            }

            override fun dismiss() {
                showDialog.value = false
            }

            override suspend fun showConfirm(): Boolean {
                show()
                return result.receive().also {
                    dismiss()
                }
            }
        }
    }
    val dialogComposable = @Composable {
        if (showDialog.value) {
            ConfirmDialog(title, content, onCancel = {
                scope.launch {
                    result.send(false)
                }
            }, onConfirm = {
                scope.launch {
                    result.send(true)
                }
            })
        }
    }
    dialogComposable()
    return handle
}

@Composable
fun ConfirmDialog(title: String, content: String, onConfirm: () -> Unit, onCancel: () -> Unit) {
    WindowDialog(
        onDismissRequest = { }, show = true, title = title
    ) {
        Column {
            Text(text = content)
            Row(
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.padding(top = 12.dp)
            ) {
                TextButton(
                    text = stringResource(id = android.R.string.cancel),
                    onClick = onCancel,
                    modifier = Modifier.weight(1f),
                )
                TextButton(
                    text = stringResource(id = android.R.string.ok),
                    onClick = onConfirm,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.textButtonColorsPrimary()
                )
            }
        }
    }
}

@Composable
fun rememberVerifyPwdDialog(
    title: String, errorText: String, onVerify: (String) -> Boolean
): VerifyPwdDialogHandle {
    val showDialog = rememberSaveable {
        mutableStateOf(false)
    }
    val result = remember {
        Channel<Boolean>()
    }
    val scope = rememberCoroutineScope()
    val handle = remember {
        object : VerifyPwdDialogHandle {
            override fun show() {
                showDialog.value = true
            }

            override fun dismiss() {
                showDialog.value = false
            }

            override suspend fun showConfirm(): Boolean {
                show()
                return result.receive().also {
                    dismiss()
                }
            }
        }
    }
    val dialogComposable = @Composable {
        if (showDialog.value) {
            VerifyPwdDialog(title, errorText, onCancel = {
                scope.launch {
                    result.send(false)
                }
            }, onVerify, onVerifySuccess = {
                scope.launch {
                    result.send(true)
                }
            })
        }
    }
    dialogComposable()
    return handle
}

@Composable
fun VerifyPwdDialog(
    title: String,
    errorText: String,
    onCancel: () -> Unit,
    onVerify: suspend (String) -> Boolean,
    onVerifySuccess: () -> Unit,
) {
    val text = rememberSaveable {
        mutableStateOf("")
    }
    val hasError = remember {
        mutableStateOf(false)
    }
    val scope = rememberCoroutineScope()

    suspend fun verifyPassword(pwd: String) {
        if (onVerify(pwd)) {
            onVerifySuccess()
        } else {
            hasError.value = true
        }
    }

    WindowDialog(
        show = true,
        title = title,
        onDismissRequest = { },
    ) {
        TextField(
            value = text.value,
            onValueChange = { text.value = it },
            singleLine = true,
            keyboardOptions = KeyboardOptions.Default.copy(
                keyboardType = KeyboardType.NumberPassword, imeAction = ImeAction.Done
            ),
            keyboardActions = KeyboardActions {
                scope.launch {
                    verifyPassword(text.value)
                }
            },
            colors = TextFieldDefaults.textFieldColors(labelColor = if (hasError.value) Color.Red else MiuixTheme.colorScheme.onSecondaryContainer),
            label = if (hasError.value) {
                errorText
            } else {
                ""
            }
        )
        Row(
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.padding(top = 12.dp)
        ) {
            TextButton(
                text = stringResource(id = android.R.string.cancel),
                onClick = onCancel,
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(20.dp))
            TextButton(
                text = stringResource(id = android.R.string.ok),
                onClick = { scope.launch { verifyPassword(text.value) } },
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.textButtonColorsPrimary()
            )
        }
    }
}

@Composable
fun rememberSetPwdDialog(
    title: String,
): InputDialogHandle {
    val showDialog = rememberSaveable {
        mutableStateOf(false)
    }
    val result = remember {
        Channel<String>()
    }
    val scope = rememberCoroutineScope()
    val handle = remember {
        object : InputDialogHandle {
            override fun show() {
                showDialog.value = true
            }

            override fun dismiss() {
                showDialog.value = false
            }

            override suspend fun awaitInput(): String {
                show()
                return result.receive().also {
                    dismiss()
                }
            }
        }
    }
    val dialogComposable = @Composable {
        if (showDialog.value) {
            SetPwdDialog(title = title, onCancel = {
                scope.launch {
                    result.send("")
                }
            }, onConfirm = {
                scope.launch {
                    result.send(it)
                }
            })
        }
    }
    dialogComposable()
    return handle
}

@Composable
fun SetPwdDialog(
    title: String, onCancel: () -> Unit, onConfirm: (String) -> Unit
) {
    val text = rememberSaveable {
        mutableStateOf("")
    }

    val maxCount = 16
    var curCount by remember {
        mutableIntStateOf(text.value.length)
    }

    WindowDialog(
        show = true, onDismissRequest = { }, title = title
    ) {
        TextField(
            value = text.value,
            onValueChange = {
                text.value = it.trim().take(16)
                curCount = text.value.length
            },
            singleLine = true,
            keyboardOptions = KeyboardOptions.Default.copy(
                keyboardType = KeyboardType.NumberPassword, imeAction = ImeAction.Done
            ),
            keyboardActions = KeyboardActions {
                if (curCount >= 4) {
                    onConfirm(text.value)
                }
            },
            label = stringResource(id = R.string.text_password_placeholder),
            useLabelAsPlaceholder = true,
            trailingIcon = {
                Text(text = "$curCount/$maxCount", modifier = Modifier.padding(end = 8.dp))
            },
        )
        Row(
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.padding(top = 12.dp)
        ) {
            TextButton(
                text = stringResource(id = android.R.string.cancel),
                onClick = { onCancel() },
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(20.dp))
            TextButton(
                text = stringResource(id = android.R.string.ok),
                onClick = { if (curCount >= 4) onConfirm(text.value) },
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.textButtonColorsPrimary()
            )
        }
    }
}