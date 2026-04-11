package com.example.bitrix24.ra;

import jakarta.resource.NotSupportedException;
import jakarta.resource.ResourceException;
import jakarta.resource.cci.Connection;
import jakarta.resource.cci.ConnectionFactory;
import jakarta.resource.cci.ConnectionSpec;
import jakarta.resource.cci.RecordFactory;
import jakarta.resource.cci.ResourceAdapterMetaData;
import jakarta.resource.spi.ConnectionManager;

import javax.naming.Reference;
import java.io.Serial;
import java.io.Serializable;

public class Bitrix24ConnectionFactoryImpl implements ConnectionFactory, Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    private final Bitrix24ManagedConnectionFactory mcf;
    private final ConnectionManager connectionManager;
    private transient Reference reference;

    public Bitrix24ConnectionFactoryImpl() {
        this.mcf = null;
        this.connectionManager = null;
    }

    public Bitrix24ConnectionFactoryImpl(Bitrix24ManagedConnectionFactory mcf, ConnectionManager connectionManager) {
        this.mcf = mcf;
        this.connectionManager = connectionManager;
    }

    @Override
    public Connection getConnection() throws ResourceException {
        return getConnection(null);
    }

    @Override
    public Connection getConnection(ConnectionSpec properties) throws ResourceException {
        if (properties != null) {
            throw new NotSupportedException("ConnectionSpec is not supported");
        }
        if (mcf == null) {
            throw new ResourceException("ManagedConnectionFactory is not initialized");
        }

        if (connectionManager != null) {
            return (Connection) connectionManager.allocateConnection(mcf, null);
        }

        Bitrix24ManagedConnection managedConnection = new Bitrix24ManagedConnection(mcf);
        return (Connection) managedConnection.getConnection(null, null);
    }

    @Override
    public RecordFactory getRecordFactory() {
        return new Bitrix24RecordFactory();
    }

    @Override
    public ResourceAdapterMetaData getMetaData() {
        return new Bitrix24ResourceAdapterMetaData();
    }

    @Override
    public Reference getReference() {
        return reference;
    }

    @Override
    public void setReference(Reference reference) {
        this.reference = reference;
    }
}
