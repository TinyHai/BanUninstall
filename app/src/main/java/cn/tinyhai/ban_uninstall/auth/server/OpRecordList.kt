package cn.tinyhai.ban_uninstall.auth.server

import cn.tinyhai.ban_uninstall.auth.entities.OpRecord
import cn.tinyhai.ban_uninstall.auth.entities.OpResult

class OpRecordList {

    private val records: MutableList<OpRecord> = ArrayList()

    fun add(opRecord: OpRecord, result: OpResult) {
        synchronized(records) {
            records.add(opRecord.copy(resultOrdinal = result.ordinal))
        }
    }

    fun toList(): List<OpRecord> {
        return synchronized(records) {
            records.toList()
        }
    }
}