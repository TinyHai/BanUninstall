package cn.tinyhai.ban_uninstall.utils

import android.os.SystemClock
import java.util.concurrent.atomic.AtomicBoolean

interface Updater<T> {
    fun requestUpdate()
    fun finishUpdate(newValue: T)
}

class SimpleUpdater<T>(
    initValue: T? = null,
    private val shouldUpdate: (T?, T & Any) -> Boolean,
    private val onRequestUpdate: () -> Unit,
    private val onUpdateSuccess: () -> Unit,
) : Updater<T & Any> {

    private var updateStartTime = 0L

    private val updating = AtomicBoolean(false)

    private var oldValue: T? = initValue

    override fun requestUpdate() {
        if (updating.compareAndSet(false, true)) {
            updateStartTime = SystemClock.elapsedRealtime()
            onRequestUpdate()
        }
    }

    override fun finishUpdate(newValue: T & Any) {
        if (shouldUpdate(oldValue, newValue)) {
            oldValue = newValue
            onUpdateSuccess()
        }
        updating.compareAndSet(true, false)
        val updateElapsed = SystemClock.elapsedRealtime() - updateStartTime
        XPLogUtils.log("finishUpdate: cost ${updateElapsed}ms")
    }
}