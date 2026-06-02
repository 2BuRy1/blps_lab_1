package com.example.bitrix24.ra;

import jakarta.resource.ResourceException;
import jakarta.resource.spi.ActivationSpec;
import jakarta.resource.spi.BootstrapContext;
import jakarta.resource.spi.ResourceAdapter;
import jakarta.resource.spi.ResourceAdapterInternalException;
import jakarta.resource.spi.endpoint.MessageEndpointFactory;
import javax.transaction.xa.XAResource;

import java.io.Serial;
import java.io.Serializable;

public class Bitrix24ResourceAdapter implements ResourceAdapter, Serializable {
    @Serial
    private static final long serialVersionUID = 1L;
    private static final XAResource[] EMPTY_XA_RESOURCES = new XAResource[0];

    @Override
    public void start(BootstrapContext ctx) throws ResourceAdapterInternalException {}

    @Override
    public void stop() {}

    @Override
    public void endpointActivation(MessageEndpointFactory endpointFactory, ActivationSpec spec) {}

    @Override
    public void endpointDeactivation(MessageEndpointFactory endpointFactory, ActivationSpec spec) {}

    @Override
    public XAResource[] getXAResources(ActivationSpec[] specs) throws ResourceException {
        return EMPTY_XA_RESOURCES;
    }

    @Override
    public boolean equals(Object obj) {
        return obj instanceof Bitrix24ResourceAdapter;
    }

    @Override
    public int hashCode() {
        return Bitrix24ResourceAdapter.class.hashCode();
    }
}
