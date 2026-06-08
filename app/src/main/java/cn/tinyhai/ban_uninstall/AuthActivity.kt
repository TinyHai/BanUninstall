package cn.tinyhai.ban_uninstall

import android.content.pm.ApplicationInfo
import android.graphics.Color
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.util.Log
import android.view.Window
import androidx.activity.addCallback
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toDrawable
import cn.tinyhai.ban_uninstall.auth.client.AuthClient
import cn.tinyhai.ban_uninstall.auth.client.AuthClient.Companion.isDummy
import cn.tinyhai.ban_uninstall.auth.entities.AuthData
import cn.tinyhai.ban_uninstall.auth.entities.OpType
import cn.tinyhai.ban_uninstall.ui.component.rememberVerifyPwdDialog
import cn.tinyhai.ban_uninstall.ui.theme.AppTheme
import cn.tinyhai.ban_uninstall.utils.BiometricUtils
import cn.tinyhai.ban_uninstall.vm.AuthViewModel
import coil.compose.AsyncImage
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.window.WindowDialog

private const val TAG = "AuthActivity"

class AuthActivity : AppCompatActivity() {

    private val viewModel by viewModels<AuthViewModel>()

    override fun onCreate(savedInstanceState: Bundle?) {
        supportRequestWindowFeature(Window.FEATURE_NO_TITLE)
        super.onCreate(savedInstanceState)
        setFinishOnTouchOutside(false)
        AuthClient.inject(intent)

        val authClient = AuthClient()
        val authData = AuthClient.parseAuthData(intent)
        if (authClient.isDummy || authData.isEmpty) {
            finish()
            return
        }

        viewModel.setup(authClient, authData)

        if (!viewModel.isValid()) {
            finish()
            return
        }

        onBackPressedDispatcher.addCallback {
            viewModel.onPrevent()
            finish()
        }

        Log.i(TAG, "$this: $authData")

        setContent {
            AppTheme {
                val scope = rememberCoroutineScope()
                val context = LocalContext.current
                val verifyPwdDialog = rememberVerifyPwdDialog(
                    title = stringResource(id = R.string.title_verify_password),
                    errorText = stringResource(R.string.text_verify_password_error),
                    onVerify = { viewModel.authenticate(it) },
                )

                val isDual = authData.appInfo.uid / 100_000 > 0
                val appLabel = authData.appInfo.loadLabel(context.packageManager).toString().let {
                    if (isDual) stringResource(R.string.text_app_label_dual, it) else it
                }

                val opText = when (authData.opType) {
                    OpType.ClearData -> stringResource(
                        id = R.string.text_clear_the_app_data,
                        appLabel
                    )

                    OpType.Uninstall -> stringResource(
                        id = R.string.text_uninstall_the_app,
                        appLabel
                    )
                }
                val authTitle = stringResource(R.string.title_auth)
                val authDescription = stringResource(R.string.description_auth, opText)
                ConfirmDialogContent(
                    authData = viewModel.authData,
                    onConfirm = {
                        scope.launch {
                            if (viewModel.hasPwd) {
                                if (verifyPwdDialog.verify()) {
                                    finishOp(true)
                                }
                            } else {
                                BiometricUtils.auth(context, authTitle, authDescription)
                                    .onSuccess { success ->
                                        if (success) {
                                            finishOp(true)
                                        }
                                    }
                            }
                        }
                    },
                    onCancel = {
                        finishOp(false)
                    },
                )
            }
        }
        window.setBackgroundDrawableResource(android.R.color.transparent)
    }

    private fun finishOp(agree: Boolean) {
        if (agree) {
            viewModel.onAgree()
        } else {
            viewModel.onPrevent()
        }
        finish()
    }

    override fun finish() {
        super.finishAndRemoveTask()
    }
}

@Composable
private fun ConfirmDialogContent(
    authData: AuthData,
    onConfirm: () -> Unit,
    onCancel: () -> Unit
) {
    val context = LocalContext.current
    val opType = authData.opType
    val unknownApp = stringResource(R.string.text_unknown_app)
    val rootLabel = stringResource(R.string.text_label_root)
    val shellLabel = stringResource(R.string.text_label_shell)
    val systemLabel = stringResource(R.string.text_label_system)
    val opLabel by produceState("") {
        value = authData.opAppInfo?.loadLabel(context.packageManager)?.toString()
            ?: when (authData.opUid) {
                0 -> rootLabel
                2000 -> shellLabel
                1000 -> systemLabel
                else -> unknownApp
            }
    }
    val opTypeText = when (opType) {
        OpType.ClearData -> stringResource(R.string.text_clear_app_data)
        OpType.Uninstall -> stringResource(R.string.text_uninstall_app)
    }
    val isDual = authData.opUid / 100_000 > 0
    val opContentText = buildString {
        append(if (isDual) stringResource(id = R.string.text_app_label_dual, opLabel) else opLabel)
        append("(${authData.opUid}) ")
        append(stringResource(id = R.string.text_try_op, opTypeText.lowercase()))
    }
    WindowDialog(true, title = opTypeText, onDismissRequest = { onCancel() }) {
        Text(text = opContentText, style = MiuixTheme.textStyles.title4)
        AppInfoContent(authData.appInfo)
        Row(
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.padding(top = 12.dp)
        ) {
            TextButton(text = stringResource(R.string.text_prevent), onClick = { onCancel() }, modifier = Modifier.weight(1f),)
            Spacer(Modifier.width(20.dp))
            TextButton(
                text = stringResource(R.string.text_verify_and_allow),
                onClick = {
                    onConfirm()
                },
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.textButtonColorsPrimary()
            )
        }
    }
}

@Composable
private fun AppInfoContent(appInfo: ApplicationInfo) {
    val context = LocalContext.current
    val icon by produceState<Drawable>(Color.TRANSPARENT.toDrawable()) {
        appInfo.loadIcon(context.packageManager)?.let { value = it }
    }
    val label by produceState("") {
        value = appInfo.loadLabel(context.packageManager).toString()
    }
    val isDual = appInfo.uid / 100_000 > 0
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(8.dp)) {
        AsyncImage(
            icon,
            contentDescription = label,
            modifier = Modifier.size(48.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Column {
            Text(
                text = if (isDual) stringResource(
                    id = R.string.text_app_label_dual,
                    label
                ) else label
            )
            Text(text = appInfo.packageName)
        }
    }
}