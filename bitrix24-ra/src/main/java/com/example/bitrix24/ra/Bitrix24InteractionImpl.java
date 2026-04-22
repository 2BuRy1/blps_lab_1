package com.example.bitrix24.ra;

import jakarta.resource.ResourceException;
import jakarta.resource.cci.Connection;
import jakarta.resource.cci.Interaction;
import jakarta.resource.cci.InteractionSpec;
import jakarta.resource.cci.MappedRecord;
import jakarta.resource.cci.Record;
import jakarta.resource.cci.ResourceWarning;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;

public class Bitrix24InteractionImpl implements Interaction {

    private final Bitrix24ConnectionImpl connection;
    private final Bitrix24ManagedConnectionFactory mcf;
    private boolean closed;

    public Bitrix24InteractionImpl(Bitrix24ConnectionImpl connection, Bitrix24ManagedConnectionFactory mcf) {
        this.connection = connection;
        this.mcf = mcf;
    }

    @Override
    public void close() {
        closed = true;
    }

    @Override
    public Connection getConnection() {
        return connection;
    }

    @Override
    public boolean execute(InteractionSpec ispec, Record input, Record output) throws ResourceException {
        Record result = execute(ispec, input);
        if (!(output instanceof MappedRecord<?, ?> mappedOut) || !(result instanceof MappedRecord<?, ?> mappedResult)) {
            throw new ResourceException("MappedRecord input/output is required");
        }
        mappedOut.clear();
        @SuppressWarnings("unchecked")
        Map<Object, Object> out = (Map<Object, Object>) mappedOut;
        for (Object key : mappedResult.keySet()) {
            out.put(key, mappedResult.get(key));
        }
        return true;
    }

    @Override
    public Record execute(InteractionSpec ispec, Record input) throws ResourceException {
        connection.ensureOpen();
        ensureOpen();

        if (!(input instanceof MappedRecord<?, ?> mappedInput)) {
            throw new ResourceException("MappedRecord input is required");
        }

        String operation = asString(mappedInput.get("operation"), asString(mappedInput.get("path"), null));
        String body = asString(mappedInput.get("body"), asString(mappedInput.get("payload"), "{}"));

        if (operation == null || operation.isBlank()) {
            throw new ResourceException("Request field 'operation' is required");
        }

        String baseUrl = normalizeBaseUrl(mcf.getWebhookBaseUrl());
        String targetUrl = baseUrl + operation;

        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(Math.max(1, valueOrDefault(mcf.getConnectTimeoutMillis(), 2000))))
                .build();

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(targetUrl))
                .timeout(Duration.ofMillis(Math.max(1, valueOrDefault(mcf.getReadTimeoutMillis(), 5000))))
                .header("Content-Type", "application/json")
                .header("Accept", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();

        HttpResponse<String> response;
        try {
            response = client.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (IOException | InterruptedException ex) {
            if (ex instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            throw new ResourceException("Bitrix24 HTTP call failed: " + ex.getMessage(), ex);
        }

        Bitrix24MappedRecord result = new Bitrix24MappedRecord("bitrixResponse");
        result.put("statusCode", response.statusCode());
        result.put("body", response.body() == null ? "" : response.body());

        if (response.statusCode() >= 400) {
            throw new ResourceException("Bitrix24 returned HTTP " + response.statusCode() + ": " + response.body());
        }

        return result;
    }

    @Override
    public ResourceWarning getWarnings() {
        return null;
    }

    @Override
    public void clearWarnings() {
        // no-op
    }

    private void ensureOpen() throws ResourceException {
        if (closed) {
            throw new ResourceException("Interaction is closed");
        }
    }

    private String normalizeBaseUrl(String baseUrl) {
        if (baseUrl == null || baseUrl.isBlank()) {
            throw new IllegalArgumentException("webhookBaseUrl is empty");
        }
        String normalized = baseUrl.trim();
        return normalized.endsWith("/") ? normalized : normalized + "/";
    }

    private String asString(Object value, String fallback) {
        if (value == null) {
            return fallback;
        }
        return value.toString();
    }

    private int valueOrDefault(Integer value, int defaultValue) {
        return value == null ? defaultValue : value;
    }
}
