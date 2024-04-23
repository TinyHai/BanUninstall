package cn.tinyhai.ban_uninstall.auth.server

import android.util.SparseArray

class PendingOpList {
    private var counter = 0

    private val pending = SparseArray<PendingOp>()

    fun add(op: PendingOp): Int {
        return synchronized(pending) {
            val opId = counter.also { counter += 1 }
            pending.put(opId, op)
            opId
        }
    }

    fun remove(opId: Int): PendingOp? {
        return synchronized(pending) {
            pending[opId]?.also { pending.remove(opId) }
        }
    }

    interface PendingOp {
        fun confirm()
        fun cancel()
    }
}