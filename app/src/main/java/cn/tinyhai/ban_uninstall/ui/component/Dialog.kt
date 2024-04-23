package cn.tinyhai.ban_uninstall.ui.component

import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.substring
import cn.tinyhai.ban_uninstall.R
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch

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
fun rememberVerifyPwdDialog(
    title: String,
    errorText: String,
    onVerify: (String) -> Boolean
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
            VerifyPwdDialog(
                title,
                errorText,
                onCancel = {
                    scope.launch {
                        result.send(false)
                    }
                },
                onVerify,
                onVerifySuccess = {
                    scope.launch {
                        result.send(true)
                    }
                }
            )
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

    AlertDialog(
        onDismissRequest = { },
        dismissButton = {
            TextButton(onClick = { onCancel() }) {
                Text(text = stringResource(id = android.R.string.cancel))
            }
        },
        confirmButton = {
            TextButton(onClick = {
                scope.launch {
                    verifyPassword(text.value)
                }
            }) {
                Text(text = stringResource(id = android.R.string.ok))
            }
        },
        title = {
            Text(text = title)
        },
        text = {
            OutlinedTextField(
                value = text.value,
                onValueChange = { text.value = it },
                singleLine = true,
                keyboardOptions = KeyboardOptions.Default.copy(
                    keyboardType = KeyboardType.NumberPassword,
                    imeAction = ImeAction.Done
                ),
                keyboardActions = KeyboardActions {
                    scope.launch {
                        verifyPassword(text.value)
                    }
                },
                isError = hasError.value,
                supportingText = if (hasError.value) {
                    {
                        Text(text = errorText)
                    }
                } else {
                    null
                }
            )
        }
    )
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
            SetPwdDialog(
                title = title,
                onCancel = {
                    scope.launch {
                        result.send("")
                    }
                },
                onConfirm = {
                    scope.launch {
                        result.send(it)
                    }
                }
            )
        }
    }
    dialogComposable()
    return handle
}

@Composable
fun SetPwdDialog(
    title: String,
    onCancel: () -> Unit,
    onConfirm: (String) -> Unit
) {
    val text = rememberSaveable {
        mutableStateOf("")
    }

    val maxCount = 16
    var curCount by remember {
        mutableIntStateOf(text.value.length)
    }

    AlertDialog(
        onDismissRequest = { },
        dismissButton = {
            TextButton(onClick = { onCancel() }) {
                Text(text = stringResource(id = android.R.string.cancel))
            }
        },
        confirmButton = {
            TextButton(onClick = { if (curCount >= 4) onConfirm(text.value) }) {
                Text(text = stringResource(id = android.R.string.ok))
            }
        },
        title = {
            Text(text = title)
        },
        text = {
            OutlinedTextField(
                value = text.value,
                onValueChange = {
                    text.value = it.trim().take(16)
                    curCount = text.value.length
                },
                singleLine = true,
                keyboardOptions = KeyboardOptions.Default.copy(
                    keyboardType = KeyboardType.NumberPassword,
                    imeAction = ImeAction.Done
                ),
                keyboardActions = KeyboardActions {
                    if (curCount >= 4) {
                        onConfirm(text.value)
                    }
                },
                placeholder = {
                    Text(text = stringResource(id = R.string.text_password_placeholder))
                },
                trailingIcon = {
                    Text(text = "$curCount/$maxCount")
                }
            )
        },
    )
}