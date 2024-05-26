package cn.tinyhai.ban_uninstall.auth.server

import android.util.SparseArray
import androidx.core.util.valueIterator

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

    fun contains(opId: Int): Boolean {
        return synchronized(pending) {
            pending[opId] != null
        }
    }

    fun remove(opId: Int): PendingOp? {
        return synchronized(pending) {
            pending[opId]?.also { pending.remove(opId) }
        }
    }

    fun removeAll(): List<PendingOp> {
        return synchronized(pending) {
            Iterable { pending.valueIterator() }.toList().also {
                pending.clear()
            }
        }
    }

    interface PendingOp {
        fun agree()
        fun prevent()
    }
}