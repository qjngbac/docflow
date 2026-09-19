package com.docflow.service;

import com.docflow.common.BusinessException;
import com.docflow.common.ErrorCode;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;

/** 调用仅监听本机的CRDT管理端口，执行checkpoint、恢复、断连和权威内容替换。 */
@Service
public class CrdtCheckpointService {

    private static final Logger log = LoggerFactory.getLogger(CrdtCheckpointService.class);

    private final ObjectMapper objectMapper;
    private volatile HttpClient httpClient;

    @Value("${app.crdt.admin-url:http://127.0.0.1:1235}")
    private String adminUrl;

    @Value("${app.crdt.admin-secret:docflow-local-admin}")
    private String adminSecret;

    public CrdtCheckpointService(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public Map<String, Object> status(Long documentId) {
        return request(documentId, "/checkpoint", "GET", null, "读取协作恢复点状态");
    }

    public Map<String, Object> checkpoint(Long documentId) {
        return request(documentId, "/checkpoint", "POST", null, "生成协作恢复点");
    }

    public Map<String, Object> history(Long documentId) {
        return request(documentId, "/checkpoints", "GET", null, "查询协作恢复点列表");
    }

    public Map<String, Object> restore(Long documentId, Long checkpointId) {
        return request(documentId, "/checkpoints/" + checkpointId + "/restore", "POST", null, "恢复历史协作版本");
    }

    /** 主动关闭文档连接，使权限被修改的客户端在重连时重新鉴权。 */
    public Map<String, Object> disconnect(Long documentId) {
        return request(documentId, "/connections/close", "POST", null, "关闭文档协作连接");
    }

    /** 将历史HTML转换并写入权威Y.Doc，用于版本回滚而非只改物化副本。 */
    public Map<String, Object> replaceContent(Long documentId, String html) {
        return request(documentId, "/content", "POST", Map.of("html", html == null ? "" : html), "替换协作文档内容");
    }

    private Map<String, Object> request(Long documentId, String suffix, String method, Object body, String action) {
        String path = "/documents/" + documentId + suffix;
        try {
            HttpRequest.Builder builder = HttpRequest.newBuilder()
                    .uri(URI.create(adminUrl + path))
                    .timeout(Duration.ofSeconds(15))
                    .header("Authorization", "Bearer " + adminSecret)
                    .header("Accept", "application/json");
            HttpRequest request = "POST".equals(method)
                    ? builder.header("Content-Type", "application/json")
                    .POST(body == null ? HttpRequest.BodyPublishers.noBody()
                            : HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(body)))
                    .build()
                    : builder.GET().build();
            HttpResponse<String> response = httpClient().send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                // 记录真实失败的方法、路径与上游响应，便于定位是哪个 Admin API 出错；不记录共享密钥与请求体。
                log.warn("CRDT admin call failed: action={} method={} path={} status={} body={}",
                        action, method, path, response.statusCode(), summarize(response.body()));
                throw new BusinessException(ErrorCode.CONFLICT, action + "失败，请稍后重试");
            }
            return objectMapper.readValue(response.body(), new TypeReference<>() { });
        } catch (BusinessException exception) {
            throw exception;
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            log.warn("CRDT admin call interrupted: action={} method={} path={}", action, method, path);
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, action + "被中断，请稍后重试");
        } catch (Exception exception) {
            log.warn("CRDT admin call unavailable: action={} method={} path={}", action, method, path, exception);
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "协作服务暂时不可用，请稍后重试");
        }
    }

    /** 截断上游响应体，避免把大段正文写进日志。 */
    private String summarize(String body) {
        if (body == null) {
            return "";
        }
        String trimmed = body.strip();
        return trimmed.length() <= 300 ? trimmed : trimmed.substring(0, 300) + "…";
    }

    /** 延迟创建客户端，避免本机网络栈异常导致整个Spring上下文启动失败。 */
    private HttpClient httpClient() {
        HttpClient client = httpClient;
        if (client == null) {
            synchronized (this) {
                client = httpClient;
                if (client == null) {
                    client = HttpClient.newBuilder()
                            .connectTimeout(Duration.ofSeconds(3))
                            .build();
                    httpClient = client;
                }
            }
        }
        return client;
    }
}
