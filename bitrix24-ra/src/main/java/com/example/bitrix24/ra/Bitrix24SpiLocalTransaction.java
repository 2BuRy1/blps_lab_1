package com.example.bitrix24.ra;

import jakarta.resource.spi.LocalTransaction;

public class Bitrix24SpiLocalTransaction implements LocalTransaction {
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
