package cn.tinyhai.ban_uninstall.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.util.Log
import androidx.core.content.ContextCompat
import cn.tinyhai.ban_uninstall.BuildConfig
import cn.tinyhai.ban_uninstall.MainActivity

class RestartMainReceiver : BroadcastReceiver() {
    companion object {
        private const val TAG = "RestartMainReceiver"
        private const val ACTION = "${BuildConfig.APPLICATION_ID}.RESTART_MAIN"

        fun send(context: Context) {
            val intent = Intent().apply {
                setAction(ACTION)
            }
            context.sendBroadcast(intent)
        }

        fun register(context: Context): () -> Unit {
            val receiver = RestartMainReceiver()
            ContextCompat.registerReceiver(
                context,
                receiver,
                IntentFilter(ACTION),
                ContextCompat.RECEIVER_NOT_EXPORTED
            )
            return {
                context.unregisterReceiver(receiver)
            }
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == ACTION) {
            Log.d(TAG, "restart")
            MainActivity.restart()
        }
    }
}