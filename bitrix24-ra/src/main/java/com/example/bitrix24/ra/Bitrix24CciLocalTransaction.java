package com.example.bitrix24.ra;

import jakarta.resource.cci.LocalTransaction;

public class Bitrix24CciLocalTransaction implements LocalTransaction {
    @Override
    public void begin() {
        // no-op
    }

    @Override
    public void commit() {
        // no-op
    }

    @Override
    public void rollback() {
        // no-op
    }
}
