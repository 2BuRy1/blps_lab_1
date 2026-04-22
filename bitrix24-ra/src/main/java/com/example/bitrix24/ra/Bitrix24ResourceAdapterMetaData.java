package com.example.bitrix24.ra;

import jakarta.resource.cci.ResourceAdapterMetaData;

public class Bitrix24ResourceAdapterMetaData implements ResourceAdapterMetaData {
    @Override
    public String getAdapterVersion() {
        return "0.1.0";
    }

    @Override
    public String getAdapterVendorName() {
        return "BLPS";
    }

    @Override
    public String getAdapterName() {
        return "Bitrix24 JCA Adapter";
    }

    @Override
    public String getAdapterShortDescription() {
        return "Outbound JCA adapter for Bitrix24 webhook API";
    }

    @Override
    public String getSpecVersion() {
        return "2.1";
    }

    @Override
    public String[] getInteractionSpecsSupported() {
        return new String[0];
    }

    @Override
    public boolean supportsExecuteWithInputAndOutputRecord() {
        return true;
    }

    @Override
    public boolean supportsExecuteWithInputRecordOnly() {
        return true;
    }

    @Override
    public boolean supportsLocalTransactionDemarcation() {
        return false;
    }
}
