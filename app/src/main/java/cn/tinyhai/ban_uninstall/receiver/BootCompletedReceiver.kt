package cn.tinyhai.ban_uninstall.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import cn.tinyhai.ban_uninstall.utils.tryToInjectIntoSystemServer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class BootCompletedReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "BootCompletedReceiver"
        private const val ACTION = Intent.ACTION_BOOT_COMPLETED
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == ACTION) {
            Log.d(TAG, "BootCompleted")
            CoroutineScope(Dispatchers.Unconfined).launch {
                tryToInjectIntoSystemServer()
            }
        }
    }
}