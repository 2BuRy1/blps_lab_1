package com.example.bitrix24.ra;

import jakarta.resource.ResourceException;
import jakarta.resource.cci.ConnectionFactory;
import jakarta.resource.spi.ConnectionManager;
import jakarta.resource.spi.ConnectionRequestInfo;
import jakarta.resource.spi.ManagedConnection;
import jakarta.resource.spi.ManagedConnectionFactory;
import jakarta.resource.spi.ResourceAdapter;
import jakarta.resource.spi.ResourceAdapterAssociation;

import javax.security.auth.Subject;
import java.io.PrintWriter;
import java.io.Serial;
import java.io.Serializable;
import java.util.Iterator;
import java.util.Objects;
import java.util.Set;

public class Bitrix24ManagedConnectionFactory implements ManagedConnectionFactory, ResourceAdapterAssociation, Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    private transient PrintWriter logWriter;
    private ResourceAdapter resourceAdapter;

    private String webhookBaseUrl = "https://b24-a0p4gq.bitrix24.ru/rest/1/ea3xpoamp12fr371/";
    private Integer connectTimeoutMillis = 2000;
    private Integer readTimeoutMillis = 5000;

    @Override
    public Object createConnectionFactory(ConnectionManager cxManager) throws ResourceException {
        return new Bitrix24ConnectionFactoryImpl(this, cxManager);
    }

    @Override
    public Object createConnectionFactory() throws ResourceException {
        return new Bitrix24ConnectionFactoryImpl(this, null);
    }

    @Override
    public ManagedConnection createManagedConnection(Subject subject, ConnectionRequestInfo cxRequestInfo) {
        return new Bitrix24ManagedConnection(this);
    }

    @Override
    public ManagedConnection matchManagedConnections(Set connectionSet, Subject subject, ConnectionRequestInfo cxRequestInfo) {
        Iterator<?> it = connectionSet.iterator();
        while (it.hasNext()) {
            Object candidate = it.next();
            if (candidate instanceof Bitrix24ManagedConnection connection) {
                return connection;
            }
        }
        return null;
    }

    @Override
    public void setLogWriter(PrintWriter out) {
        this.logWriter = out;
    }

    @Override
    public PrintWriter getLogWriter() {
        return logWriter;
    }

    @Override
    public ResourceAdapter getResourceAdapter() {
        return resourceAdapter;
    }

    @Override
    public void setResourceAdapter(ResourceAdapter ra) {
        this.resourceAdapter = ra;
    }

    public String getWebhookBaseUrl() {
        return webhookBaseUrl;
    }

    public void setWebhookBaseUrl(String webhookBaseUrl) {
        this.webhookBaseUrl = webhookBaseUrl;
    }

    public Integer getConnectTimeoutMillis() {
        return connectTimeoutMillis;
    }

    public void setConnectTimeoutMillis(Integer connectTimeoutMillis) {
        this.connectTimeoutMillis = connectTimeoutMillis;
    }

    public Integer getReadTimeoutMillis() {
        return readTimeoutMillis;
    }

    public void setReadTimeoutMillis(Integer readTimeoutMillis) {
        this.readTimeoutMillis = readTimeoutMillis;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Bitrix24ManagedConnectionFactory that)) return false;
        return connectTimeoutMillis == that.connectTimeoutMillis
                && readTimeoutMillis == that.readTimeoutMillis
                && Objects.equals(webhookBaseUrl, that.webhookBaseUrl);
    }

    @Override
    public int hashCode() {
        return Objects.hash(webhookBaseUrl, connectTimeoutMillis, readTimeoutMillis);
    }
}
