package com.example.bitrix24.ra;

import jakarta.resource.cci.IndexedRecord;
import jakarta.resource.cci.MappedRecord;
import jakarta.resource.cci.RecordFactory;

public class Bitrix24RecordFactory implements RecordFactory {
    @Override
    public <K, V> MappedRecord<K, V> createMappedRecord(String recordName) {
        @SuppressWarnings("unchecked")
        MappedRecord<K, V> record = (MappedRecord<K, V>) new Bitrix24MappedRecord(recordName);
        return record;
    }

    @Override
    public <E> IndexedRecord<E> createIndexedRecord(String recordName) {
        @SuppressWarnings("unchecked")
        IndexedRecord<E> record = (IndexedRecord<E>) new Bitrix24IndexedRecord(recordName);
        return record;
    }
}
