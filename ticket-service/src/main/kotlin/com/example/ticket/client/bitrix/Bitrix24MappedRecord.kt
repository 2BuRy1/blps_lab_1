package com.example.ticket.client.bitrix

import jakarta.resource.cci.MappedRecord
import java.io.Serializable
import java.util.LinkedHashMap

class Bitrix24MappedRecord(
    private var recordName: String = "bitrixRecord",
    private var recordShortDescription: String? = null,
) : LinkedHashMap<String, Any>(), MappedRecord<String, Any>, Serializable {

    override fun getRecordName(): String = recordName

    override fun setRecordName(name: String?) {
        recordName = name ?: "bitrixRecord"
    }

    override fun getRecordShortDescription(): String? = recordShortDescription

    override fun setRecordShortDescription(description: String?) {
        recordShortDescription = description
    }

    public override fun clone(): Any {
        val copy = Bitrix24MappedRecord(recordName, recordShortDescription)
        copy.putAll(this)
        return copy
    }
}
