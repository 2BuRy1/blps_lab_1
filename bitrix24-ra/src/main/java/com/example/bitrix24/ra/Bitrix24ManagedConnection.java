package com.example.bitrix24.ra;

import jakarta.resource.ResourceException;
import jakarta.resource.NotSupportedException;
import jakarta.resource.spi.ConnectionEvent;
import jakarta.resource.spi.ConnectionEventListener;
import jakarta.resource.spi.ConnectionRequestInfo;
import jakarta.resource.spi.LocalTransaction;
import jakarta.resource.spi.ManagedConnection;
import jakarta.resource.spi.ManagedConnectionMetaData;

import javax.security.auth.Subject;
import javax.transaction.xa.XAResource;
import java.io.PrintWriter;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public class Bitrix24ManagedConnection implements ManagedConnection {

    private final Bitrix24ManagedConnectionFactory mcf;
    private final List<ConnectionEventListener> listeners = new CopyOnWriteArrayList<>();

    private volatile PrintWriter logWriter;

    public Bitrix24ManagedConnection(Bitrix24ManagedConnectionFactory mcf) {
        this.mcf = mcf;
    }

    @Override
    public Object getConnection(Subject subject, ConnectionRequestInfo cxRequestInfo) {
        return new Bitrix24ConnectionImpl(this, mcf);
    }

    @Override
    public void destroy() {}

    @Override
    public void cleanup() {}

    @Override
    public void associateConnection(Object connection) throws ResourceException {
        if (!(connection instanceof Bitrix24ConnectionImpl handle)) {
            throw new ResourceException("Unsupported connection handle type: " + connection);
        }
        handle.associate(this, mcf);
    }

    @Override
    public void addConnectionEventListener(ConnectionEventListener listener) {
        listeners.add(listener);
    }

    @Override
    public void removeConnectionEventListener(ConnectionEventListener listener) {
        listeners.remove(listener);
    }

    @Override
    public XAResource getXAResource() throws ResourceException {
        throw new ResourceException("XA transactions are not supported");
    }

    @Override
    public LocalTransaction getLocalTransaction() throws ResourceException {
        throw new NotSupportedException("LocalTransaction is not supported for NoTransaction adapter");
    }

    @Override
    public ManagedConnectionMetaData getMetaData() {
        return new Bitrix24ManagedConnectionMetaData();
    }

    @Override
    public void setLogWriter(PrintWriter out) {
        this.logWriter = out;
    }

    @Override
    public PrintWriter getLogWriter() {
        return logWriter;
    }

    void fireConnectionClosed(Bitrix24ConnectionImpl handle) {
        ConnectionEvent event = new ConnectionEvent(this, ConnectionEvent.CONNECTION_CLOSED);
        event.setConnectionHandle(handle);
        for (ConnectionEventListener listener : listeners) {
            listener.connectionClosed(event);
        }
    }
}
