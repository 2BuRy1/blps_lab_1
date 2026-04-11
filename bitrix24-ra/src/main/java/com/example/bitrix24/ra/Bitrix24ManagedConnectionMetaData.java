package com.example.bitrix24.ra;

import jakarta.resource.spi.ManagedConnectionMetaData;

public class Bitrix24ManagedConnectionMetaData implements ManagedConnectionMetaData {
    @Override
    public String getEISProductName() {
        return "Bitrix24";
    }

    @Override
    public String getEISProductVersion() {
        return "REST";
    }

    @Override
    public int getMaxConnections() {
        return 50;
    }

    @Override
    public String getUserName() {
        return "webhook";
    }
}
