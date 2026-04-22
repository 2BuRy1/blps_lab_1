package com.example.bitrix24.ra;

import jakarta.resource.cci.ConnectionMetaData;

public class Bitrix24ConnectionMetaData implements ConnectionMetaData {
    @Override
    public String getEISProductName() {
        return "Bitrix24";
    }

    @Override
    public String getEISProductVersion() {
        return "REST";
    }

    @Override
    public String getUserName() {
        return "webhook";
    }
}
