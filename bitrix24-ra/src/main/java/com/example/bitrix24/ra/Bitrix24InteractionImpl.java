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
import java.util.logging.Logger;

public class Bitrix24InteractionImpl implements Interaction {
    private static final Logger log = Logger.getLogger(Bitrix24InteractionImpl.class.getName());
    private static final String FIELD_OPERATION = "operation";
    private static final String FIELD_PATH = "path";
    private static final String FIELD_BODY = "body";
    private static final String FIELD_PAYLOAD = "payload";
    private static final String FIELD_STATUS_CODE = "statusCode";
    private static final String CONTENT_TYPE_JSON = "application/json";
    private static final String DEFAULT_PAYLOAD = "{}";
    private static final int DEFAULT_CONNECT_TIMEOUT_MILLIS = 2000;
    private static final int DEFAULT_READ_TIMEOUT_MILLIS = 5000;

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
        copyMappedRecord(mappedResult, mappedOut);
        return true;
    }

    @Override
    public Record execute(InteractionSpec ispec, Record input) throws ResourceException {
        connection.ensureOpen();
        ensureOpen();

        if (!(input instanceof MappedRecord<?, ?> mappedInput)) {
            throw new ResourceException("MappedRecord input is required");
        }

        String operation = extractOperation(mappedInput);
        if (operation == null || operation.isBlank()) {
            throw new ResourceException("Request field 'operation' is required");
        }

        String body = extractBody(mappedInput);
        String baseUrl = normalizeBaseUrl(mcf.getWebhookBaseUrl());
        String targetUrl = baseUrl + operation;
        int connectTimeout = Math.max(1, valueOrDefault(mcf.getConnectTimeoutMillis(), DEFAULT_CONNECT_TIMEOUT_MILLIS));
        int readTimeout = Math.max(1, valueOrDefault(mcf.getReadTimeoutMillis(), DEFAULT_READ_TIMEOUT_MILLIS));

        log.info(() -> "Bitrix24 RA execute: targetUrl=" + targetUrl
                + ", connectTimeoutMs=" + connectTimeout
                + ", readTimeoutMs=" + readTimeout);

        HttpClient client = buildClient(connectTimeout);
        HttpRequest request = buildRequest(targetUrl, readTimeout, body);

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
        result.put(FIELD_STATUS_CODE, response.statusCode());
        result.put("body", response.body() == null ? "" : response.body());

        log.info(() -> "Bitrix24 RA response: statusCode=" + response.statusCode());

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
    public void clearWarnings() {}

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

    private String extractOperation(MappedRecord<?, ?> mappedInput) {
        return asString(mappedInput.get(FIELD_OPERATION), asString(mappedInput.get(FIELD_PATH), null));
    }

    private String extractBody(MappedRecord<?, ?> mappedInput) {
        return asString(mappedInput.get(FIELD_BODY), asString(mappedInput.get(FIELD_PAYLOAD), DEFAULT_PAYLOAD));
    }

    private HttpClient buildClient(int connectTimeoutMillis) {
        return HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(connectTimeoutMillis))
                .build();
    }

    private HttpRequest buildRequest(String targetUrl, int readTimeoutMillis, String body) {
        return HttpRequest.newBuilder()
                .uri(URI.create(targetUrl))
                .timeout(Duration.ofMillis(readTimeoutMillis))
                .header("Content-Type", CONTENT_TYPE_JSON)
                .header("Accept", CONTENT_TYPE_JSON)
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();
    }

    private void copyMappedRecord(MappedRecord<?, ?> source, MappedRecord<?, ?> target) {
        target.clear();
        @SuppressWarnings("unchecked")
        Map<Object, Object> out = (Map<Object, Object>) target;
        for (Object key : source.keySet()) {
            out.put(key, source.get(key));
        }
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
