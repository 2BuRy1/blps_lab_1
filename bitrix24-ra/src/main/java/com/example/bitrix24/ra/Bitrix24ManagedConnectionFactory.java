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
    private static final String ENV_WEBHOOK_BASE_URL = "BITRIX_WEBHOOK_BASE_URL";
    private static final String ENV_CONNECT_TIMEOUT_MILLIS = "BITRIX_CONNECT_TIMEOUT_MS";
    private static final String ENV_READ_TIMEOUT_MILLIS = "BITRIX_READ_TIMEOUT_MS";
    private static final int DEFAULT_CONNECT_TIMEOUT_MILLIS = 2000;
    private static final int DEFAULT_READ_TIMEOUT_MILLIS = 5000;

    private transient PrintWriter logWriter;
    private ResourceAdapter resourceAdapter;

    private String webhookBaseUrl = "https://b24-a0p4gq.bitrix24.ru/rest/17/we34lz732rxm6e6z/";
    private Integer connectTimeoutMillis = DEFAULT_CONNECT_TIMEOUT_MILLIS;
    private Integer readTimeoutMillis = DEFAULT_READ_TIMEOUT_MILLIS;

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
        return resolveStringEnv(ENV_WEBHOOK_BASE_URL, webhookBaseUrl);
    }

    public void setWebhookBaseUrl(String webhookBaseUrl) {
        this.webhookBaseUrl = webhookBaseUrl;
    }

    public Integer getConnectTimeoutMillis() {
        return resolveIntegerEnv(ENV_CONNECT_TIMEOUT_MILLIS, connectTimeoutMillis);
    }

    public void setConnectTimeoutMillis(Integer connectTimeoutMillis) {
        this.connectTimeoutMillis = connectTimeoutMillis;
    }

    public Integer getReadTimeoutMillis() {
        return resolveIntegerEnv(ENV_READ_TIMEOUT_MILLIS, readTimeoutMillis);
    }

    public void setReadTimeoutMillis(Integer readTimeoutMillis) {
        this.readTimeoutMillis = readTimeoutMillis;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof Bitrix24ManagedConnectionFactory that)) {
            return false;
        }
        return Objects.equals(connectTimeoutMillis, that.connectTimeoutMillis)
                && Objects.equals(readTimeoutMillis, that.readTimeoutMillis)
                && Objects.equals(webhookBaseUrl, that.webhookBaseUrl);
    }

    @Override
    public int hashCode() {
        return Objects.hash(webhookBaseUrl, connectTimeoutMillis, readTimeoutMillis);
    }

    private String resolveStringEnv(String envName, String fallback) {
        String value = System.getenv(envName);
        return value != null && !value.isBlank() ? value : fallback;
    }

    private Integer resolveIntegerEnv(String envName, Integer fallback) {
        String value = System.getenv(envName);
        if (value == null || value.isBlank()) {
            return fallback;
        }
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }
}
