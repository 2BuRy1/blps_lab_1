package com.example.bitrix24.ra;

import jakarta.resource.cci.IndexedRecord;

import java.io.Serial;
import java.io.Serializable;
import java.util.ArrayList;

public class Bitrix24IndexedRecord extends ArrayList<Object> implements IndexedRecord<Object>, Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    private String recordName;
    private String recordShortDescription;

    public Bitrix24IndexedRecord(String recordName) {
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
}
