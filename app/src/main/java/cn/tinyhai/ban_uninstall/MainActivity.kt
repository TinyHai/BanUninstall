package cn.tinyhai.ban_uninstall

import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.ui.NavDisplay
import cn.tinyhai.ban_uninstall.transact.client.TransactClient
import cn.tinyhai.ban_uninstall.ui.navigation3.Route
import cn.tinyhai.ban_uninstall.ui.navigation3.rememberNavigator
import cn.tinyhai.ban_uninstall.ui.screen.BannedAppScreen
import cn.tinyhai.ban_uninstall.ui.screen.MainScreen
import cn.tinyhai.ban_uninstall.ui.screen.OpRecordScreen
import cn.tinyhai.ban_uninstall.ui.theme.AppTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.i("MainActivity", intent.toString())
        TransactClient.inject(intent)
        enableEdgeToEdge()
        setContent {
            AppTheme {
//                DestinationsNavHost(NavGraphs.root, defaultTransitions = DefaultFadingTransitions)
                val navigator = rememberNavigator(Route.Main)
                NavDisplay(navigator.backStack, entryProvider = entryProvider {
                    entry<Route.Main> {
                        MainScreen(navigator)
                    }
                    entry<Route.BannedApp> {
                        BannedAppScreen(navigator)
                    }
                    entry<Route.OpRecord> {
                        OpRecordScreen(navigator)
                    }
                })
            }
        }
    }

    companion object {
        fun restart() {
            val intent = Intent(App.app, MainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            }
            App.app.startActivity(intent)
        }
    }
}
