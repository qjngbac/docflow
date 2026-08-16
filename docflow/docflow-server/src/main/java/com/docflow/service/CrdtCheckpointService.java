package com.docflow.service;

import com.docflow.common.BusinessException;
import com.docflow.common.ErrorCode;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;

@Service
public class CrdtCheckpointService {

    private final ObjectMapper objectMapper;
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(3))
            .build();

    @Value("${app.crdt.admin-url:http://127.0.0.1:1235}")
    private String adminUrl;

    @Value("${app.crdt.admin-secret:docflow-local-admin}")
    private String adminSecret;

    public CrdtCheckpointService(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public Map<String, Object> status(Long documentId) {
        return request(documentId, "/checkpoint", "GET");
    }

    public Map<String, Object> checkpoint(Long documentId) {
        return request(documentId, "/checkpoint", "POST");
    }

    public Map<String, Object> history(Long documentId) {
        return request(documentId, "/checkpoints", "GET");
    }

    public Map<String, Object> restore(Long documentId, Long checkpointId) {
        return request(documentId, "/checkpoints/" + checkpointId + "/restore", "POST");
    }

    private Map<String, Object> request(Long documentId, String suffix, String method) {
        try {
            HttpRequest.Builder builder = HttpRequest.newBuilder()
                    .uri(URI.create(adminUrl + "/documents/" + documentId + suffix))
                    .timeout(Duration.ofSeconds(15))
                    .header("Authorization", "Bearer " + adminSecret)
                    .header("Accept", "application/json");
            HttpRequest request = "POST".equals(method)
                    ? builder.POST(HttpRequest.BodyPublishers.noBody()).build()
                    : builder.GET().build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                throw new BusinessException(ErrorCode.CONFLICT, "CRDT checkpoint validation or compaction failed");
            }
            return objectMapper.readValue(response.body(), new TypeReference<>() { });
        } catch (BusinessException exception) {
            throw exception;
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "CRDT checkpoint request was interrupted");
        } catch (Exception exception) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "CRDT checkpoint service is unavailable");
        }
    }
}
