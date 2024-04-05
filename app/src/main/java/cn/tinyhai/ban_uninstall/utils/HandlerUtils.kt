package cn.tinyhai.ban_uninstall.utils

import android.os.Handler
import android.os.HandlerThread
import android.os.Looper

object HandlerUtils {
    private val handler: Handler = Handler(Looper.getMainLooper())

    fun checkMainThread(): Boolean {
        return handler.looper == Looper.myLooper()
    }

    fun checkWorkerThread(): Boolean {
        return workerHandler.looper == Looper.myLooper()
    }

    fun postDelay(runnable: Runnable, delayMs: Long) {
        handler.postDelayed(runnable, delayMs)
    }

    fun removeRunnable(runnable: Runnable) {
        handler.removeCallbacks(runnable)
    }

    fun postWorker(runnable: Runnable) {
        workerHandler.post(runnable)
    }

    fun postWorkerDelay(runnable: Runnable, delayMs: Long) {
        workerHandler.postDelayed(runnable, delayMs)
    }

    fun removeWorkerRunnable(runnable: Runnable) {
        workerHandler.removeCallbacks(runnable)
    }

    private val workerHandler by lazy {
        val t = HandlerThread("ban_uninstall_worker")
        t.start()
        Handler(t.looper).also { it.looper.setMessageLogging { LogUtils.log(it) } }
    }
}