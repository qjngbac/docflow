package com.docflow.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.docflow.common.BusinessException;
import com.docflow.common.ErrorCode;
import com.docflow.dto.ReferenceDoiRequest;
import com.docflow.dto.ReferenceRequest;
import com.docflow.entity.DocumentReference;
import com.docflow.mapper.DocumentReferenceMapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

@Service
public class DocumentReferenceService {
    private static final Pattern CITE_KEY = Pattern.compile("[A-Za-z0-9][A-Za-z0-9_.:-]{0,99}");
    private final DocumentReferenceMapper referenceMapper;
    private final PermissionService permissionService;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(4)).build();

    @Value("${app.references.crossref-url:https://api.crossref.org/works/}")
    private String crossrefUrl;

    public DocumentReferenceService(DocumentReferenceMapper referenceMapper, PermissionService permissionService,
                                    ObjectMapper objectMapper) {
        this.referenceMapper = referenceMapper;
        this.permissionService = permissionService;
        this.objectMapper = objectMapper;
    }

    public List<DocumentReference> list(Long docId, Long userId) {
        permissionService.requireReadable(docId, userId);
        return referenceMapper.selectList(new LambdaQueryWrapper<DocumentReference>()
                .eq(DocumentReference::getDocId, docId).orderByAsc(DocumentReference::getCiteKey));
    }

    @Transactional
    public DocumentReference create(Long docId, Long userId, ReferenceRequest request) {
        permissionService.requireWritable(docId, userId);
        String citeKey = normalizeKey(request.getCiteKey());
        String cslJson = normalizeCsl(request.getCslJson(), citeKey);
        String doi = normalizeDoi(request.getDoi());
        if (referenceMapper.selectOne(new LambdaQueryWrapper<DocumentReference>()
                .eq(DocumentReference::getDocId, docId).eq(DocumentReference::getCiteKey, citeKey).last("LIMIT 1")) != null) {
            throw new BusinessException(ErrorCode.CONFLICT, "Citation key already exists in this document");
        }
        DocumentReference reference = new DocumentReference();
        reference.setDocId(docId); reference.setCiteKey(citeKey); reference.setDoi(doi);
        reference.setCslJson(cslJson); reference.setCreatedBy(userId);
        referenceMapper.insert(reference);
        return reference;
    }

    @Transactional
    public DocumentReference importDoi(Long docId, Long userId, ReferenceDoiRequest request) {
        String doi = normalizeDoi(request.getDoi());
        if (doi == null) throw new BusinessException(ErrorCode.BAD_REQUEST, "A valid DOI is required");
        permissionService.requireWritable(docId, userId);
        try {
            HttpRequest httpRequest = HttpRequest.newBuilder()
                    .uri(URI.create(crossrefUrl + URLEncoder.encode(doi, StandardCharsets.UTF_8)))
                    .timeout(Duration.ofSeconds(8))
                    .header("Accept", "application/json")
                    .header("User-Agent", "DocFlow/1.0 (reference-import)").GET().build();
            HttpResponse<String> response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) throw new BusinessException(ErrorCode.BAD_REQUEST, "DOI metadata is unavailable");
            JsonNode message = objectMapper.readTree(response.body()).path("message");
            if (!message.isObject()) throw new BusinessException(ErrorCode.BAD_REQUEST, "DOI metadata is invalid");
            ReferenceRequest reference = new ReferenceRequest();
            reference.setDoi(doi);
            reference.setCiteKey(request.getCiteKey() == null || request.getCiteKey().isBlank()
                    ? defaultKey(message, doi) : request.getCiteKey());
            reference.setCslJson(objectMapper.writeValueAsString(toCsl(message, reference.getCiteKey(), doi)));
            return create(docId, userId, reference);
        } catch (BusinessException exception) {
            throw exception;
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "DOI lookup was interrupted");
        } catch (Exception exception) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "DOI metadata is unavailable");
        }
    }

    @Transactional
    public void delete(Long docId, Long referenceId, Long userId) {
        permissionService.requireWritable(docId, userId);
        DocumentReference reference = referenceMapper.selectById(referenceId);
        if (reference == null || !docId.equals(reference.getDocId())) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "Reference not found");
        }
        referenceMapper.deleteById(referenceId);
    }

    private String normalizeKey(String value) {
        String key = value == null ? "" : value.trim();
        if (!CITE_KEY.matcher(key).matches()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "Citation key may contain letters, numbers, dot, dash, underscore, and colon");
        }
        return key;
    }

    private String normalizeCsl(String raw, String citeKey) {
        try {
            JsonNode node = objectMapper.readTree(raw);
            if (node == null || !node.isObject()) throw new IllegalArgumentException();
            Map<String, Object> csl = objectMapper.convertValue(node, Map.class);
            csl.put("id", citeKey);
            if (!csl.containsKey("title") || String.valueOf(csl.get("title")).isBlank()) {
                throw new BusinessException(ErrorCode.BAD_REQUEST, "CSL JSON requires a title");
            }
            return objectMapper.writeValueAsString(csl);
        } catch (BusinessException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "CSL JSON must be a valid reference object");
        }
    }

    private String normalizeDoi(String value) {
        if (value == null || value.isBlank()) return null;
        String doi = value.trim().replaceFirst("(?i)^https?://(dx\\.)?doi\\.org/", "")
                .replaceFirst("(?i)^doi:\\s*", "").toLowerCase();
        if (!doi.matches("10\\.\\d{4,9}/[-._;()/:a-z0-9]+")) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "DOI format is invalid");
        }
        return doi;
    }

    private String defaultKey(JsonNode message, String doi) {
        String author = message.path("author").path(0).path("family").asText("ref").replaceAll("[^A-Za-z0-9]", "");
        String year = message.path("published-print").path("date-parts").path(0).path(0).asText("");
        return (author + year).isBlank() ? "doi" + Math.abs(doi.hashCode()) : (author + year).substring(0, Math.min(100, (author + year).length()));
    }

    private Map<String, Object> toCsl(JsonNode source, String key, String doi) {
        Map<String, Object> csl = new LinkedHashMap<>();
        csl.put("id", key); csl.put("type", source.path("type").asText("article-journal"));
        csl.put("title", first(source.path("title"))); csl.put("DOI", doi); csl.put("URL", "https://doi.org/" + doi);
        copyText(source, csl, "container-title", "container-title"); copyText(source, csl, "publisher", "publisher");
        copyText(source, csl, "volume", "volume"); copyText(source, csl, "issue", "issue"); copyText(source, csl, "page", "page");
        List<Map<String, String>> authors = new ArrayList<>();
        for (JsonNode author : source.path("author")) { Map<String, String> item = new LinkedHashMap<>(); item.put("family", author.path("family").asText("")); item.put("given", author.path("given").asText("")); authors.add(item); }
        if (!authors.isEmpty()) csl.put("author", authors);
        JsonNode dates = source.path("published-print").isMissingNode() ? source.path("published-online") : source.path("published-print");
        if (!dates.isMissingNode()) csl.put("issued", Map.of("date-parts", objectMapper.convertValue(dates.path("date-parts"), Object.class)));
        return csl;
    }

    private String first(JsonNode node) { return node.isArray() && !node.isEmpty() ? node.get(0).asText("") : node.asText(""); }
    private void copyText(JsonNode source, Map<String, Object> target, String sourceKey, String targetKey) { String value = first(source.path(sourceKey)); if (!value.isBlank()) target.put(targetKey, value); }
}
