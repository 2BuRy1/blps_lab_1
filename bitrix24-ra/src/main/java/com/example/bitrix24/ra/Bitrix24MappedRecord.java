package com.example.bitrix24.ra;

import jakarta.resource.cci.MappedRecord;

import java.io.Serial;
import java.io.Serializable;
import java.util.LinkedHashMap;

public class Bitrix24MappedRecord extends LinkedHashMap<String, Object> implements MappedRecord<String, Object>, Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    private String recordName;
    private String recordShortDescription;

    public Bitrix24MappedRecord(String recordName) {
        this.recordName = recordName;
    }

    @Override
    public String getRecordName() {
        return recordName;
    }

    @Override
    public void setRecordName(String recordName) {
        this.recordName = recordName;
    }

    @Override
    public void setRecordShortDescription(String description) {
        this.recordShortDescription = description;
    }

    @Override
    public String getRecordShortDescription() {
        return recordShortDescription;
    }

    @Override
    public Object clone() {
        Bitrix24MappedRecord cloned = new Bitrix24MappedRecord(this.recordName);
        cloned.recordShortDescription = this.recordShortDescription;
        cloned.putAll(this);
        return cloned;
    }
}
