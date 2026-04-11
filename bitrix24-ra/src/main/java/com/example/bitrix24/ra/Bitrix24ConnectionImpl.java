package com.example.bitrix24.ra;

import jakarta.resource.NotSupportedException;
import jakarta.resource.ResourceException;
import jakarta.resource.cci.Connection;
import jakarta.resource.cci.ConnectionMetaData;
import jakarta.resource.cci.Interaction;
import jakarta.resource.cci.LocalTransaction;
import jakarta.resource.cci.ResultSetInfo;

public class Bitrix24ConnectionImpl implements Connection {

    private Bitrix24ManagedConnection managedConnection;
    private Bitrix24ManagedConnectionFactory mcf;
    private boolean closed;

    public Bitrix24ConnectionImpl(Bitrix24ManagedConnection managedConnection, Bitrix24ManagedConnectionFactory mcf) {
        this.managedConnection = managedConnection;
        this.mcf = mcf;
    }

    @Override
    public Interaction createInteraction() throws ResourceException {
        ensureOpen();
        return new Bitrix24InteractionImpl(this, mcf);
    }

    @Override
    public LocalTransaction getLocalTransaction() throws ResourceException {
        throw new NotSupportedException("LocalTransaction is not supported");
    }

    @Override
    public ConnectionMetaData getMetaData() {
        return new Bitrix24ConnectionMetaData();
    }

    @Override
    public ResultSetInfo getResultSetInfo() throws ResourceException {
        throw new NotSupportedException("ResultSetInfo is not supported");
    }

    @Override
    public void close() {
        if (!closed) {
            closed = true;
            managedConnection.fireConnectionClosed(this);
        }
    }

    void associate(Bitrix24ManagedConnection managedConnection, Bitrix24ManagedConnectionFactory mcf) {
        this.managedConnection = managedConnection;
        this.mcf = mcf;
        this.closed = false;
    }

    void ensureOpen() throws ResourceException {
        if (closed) {
            throw new ResourceException("Connection is closed");
        }
    }
}
