package cn.tinyhai.ban_uninstall

import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import cn.tinyhai.ban_uninstall.ui.MainScreen
import cn.tinyhai.ban_uninstall.ui.theme.AppTheme
import cn.tinyhai.ban_uninstall.utils.TransactorHelper
import cn.tinyhai.ban_uninstall.vm.MainViewModel

class MainActivity : ComponentActivity() {
    private val viewModel by viewModels<MainViewModel>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.d("MainActivity", intent.toString())
        setupTransactor(intent)
        enableEdgeToEdge()
        setContent {
            AppTheme {
                MainScreen(viewModel = viewModel)
            }
        }
    }

    override fun onPause() {
        super.onPause()
        viewModel.notifyReloadIfNeeded()
    }

    override fun onStop() {
        super.onStop()
        viewModel.notifyReloadIfNeeded()
    }

    private fun setupTransactor(intent: Intent) {
        TransactorHelper.init(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setupTransactor(intent)
    }
}
