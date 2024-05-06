package cn.tinyhai.ban_uninstall

import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import cn.tinyhai.ban_uninstall.transact.client.TransactClient
import cn.tinyhai.ban_uninstall.ui.theme.AppTheme
import cn.tinyhai.compose.dragdrop.AnimatedDragDropBox
import com.ramcosta.composedestinations.DestinationsNavHost
import com.ramcosta.composedestinations.generated.NavGraphs

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.i("MainActivity", intent.toString())
        TransactClient.inject(intent)
        enableEdgeToEdge()
        setContent {
            AppTheme {
                AnimatedDragDropBox(
                    scale = 1.5f,
                    alpha = 0.6f,
                ) {
                    DestinationsNavHost(NavGraphs.root)
                }
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
