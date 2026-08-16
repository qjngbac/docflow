package com.docflow.websocket;

import com.docflow.common.BusinessException;
import com.docflow.entity.Document;
import com.docflow.service.CollaborationService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.net.URI;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Component
public class DocWebSocketHandler extends TextWebSocketHandler {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final Map<String, Set<WebSocketSession>> docSessions = new ConcurrentHashMap<>();

    @Autowired
    private CollaborationService collaborationService;

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        String docId = extractDocId(session);
        docSessions.computeIfAbsent(docId, key -> ConcurrentHashMap.newKeySet()).add(session);
        broadcastSystemEvent(docId, session, "USER_JOIN");
        broadcastOnlineUsers(docId);
        log.info("Session {} joined document {}", session.getId(), docId);
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        String docId = extractDocId(session);
        CollabMessage collabMessage = objectMapper.readValue(message.getPayload(), CollabMessage.class);
        collabMessage.setDocId(docId);
        collabMessage.setSessionId(session.getId());
        collabMessage.setUserId(userId(session));
        collabMessage.setUsername(username(session));
        if (collabMessage.getTimestamp() == null) {
            collabMessage.setTimestamp(Instant.now().toEpochMilli());
        }

        if ("DOCUMENT_UPDATE".equals(collabMessage.getType())) {
            handleDocumentUpdate(session, collabMessage);
            return;
        }
        broadcast(docId, session, collabMessage);
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {
        String docId = extractDocId(session);
        Set<WebSocketSession> sessions = docSessions.get(docId);
        boolean lastSession = false;
        if (sessions != null) {
            sessions.remove(session);
            if (sessions.isEmpty()) {
                docSessions.remove(docId);
                lastSession = true;
            }
        }
        broadcastSystemEvent(docId, session, "USER_LEAVE");
        broadcastOnlineUsers(docId);
        if (lastSession) {
            try {
                collaborationService.flushDocument(Long.valueOf(docId));
            } catch (Exception e) {
                log.error("Failed to persist document {} after the last user left", docId, e);
            }
        }
        log.info("Session {} left document {}", session.getId(), docId);
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) throws Exception {
        log.warn("WebSocket transport error: {}", session.getId(), exception);
        if (session.isOpen()) {
            session.close();
        }
    }

    private void handleDocumentUpdate(WebSocketSession session, CollabMessage message) throws IOException {
        try {
            DocumentUpdatePayload payload = objectMapper.convertValue(
                    message.getData(), DocumentUpdatePayload.class);
            Document document = collaborationService.stageUpdate(
                    Long.valueOf(message.getDocId()),
                    message.getUserId(),
                    payload.getTitle(),
                    payload.getContent(),
                    payload.getRevision());

            Map<String, Object> data = new HashMap<>();
            data.put("title", document.getTitle());
            data.put("content", document.getContent());
            data.put("revision", document.getRevision());
            data.put("persistedRevision", document.getPersistedRevision());
            message.setData(data);
            sendToAll(message.getDocId(), message);
        } catch (BusinessException e) {
            CollabMessage error = new CollabMessage();
            error.setType("ERROR");
            error.setDocId(message.getDocId());
            error.setSessionId(session.getId());
            error.setTimestamp(Instant.now().toEpochMilli());
            error.setData(Map.of("code", e.getCode(), "message", e.getMessage()));
            send(session, error);
        } catch (RuntimeException e) {
            CollabMessage error = new CollabMessage();
            error.setType("ERROR");
            error.setDocId(message.getDocId());
            error.setSessionId(session.getId());
            error.setTimestamp(Instant.now().toEpochMilli());
            error.setData(Map.of("code", 400, "message", "Invalid document update message"));
            send(session, error);
        }
    }

    private void broadcastSystemEvent(String docId, WebSocketSession source, String type) throws IOException {
        CollabMessage message = new CollabMessage();
        message.setType(type);
        message.setDocId(docId);
        message.setSessionId(source.getId());
        message.setTimestamp(Instant.now().toEpochMilli());
        message.setData(sessionUser(source));
        broadcast(docId, source, message);
    }

    private void broadcastOnlineUsers(String docId) throws IOException {
        Set<WebSocketSession> sessions = docSessions.getOrDefault(docId, Set.of());
        CollabMessage message = new CollabMessage();
        message.setType("ONLINE_USERS");
        message.setDocId(docId);
        message.setTimestamp(Instant.now().toEpochMilli());
        message.setData(Map.of(
                "count", sessions.size(),
                "users", sessions.stream().map(this::sessionUser).toList()));
        sendToAll(docId, message);
    }

    private void broadcast(String docId, WebSocketSession source, CollabMessage message) throws IOException {
        String payload = objectMapper.writeValueAsString(message);
        for (WebSocketSession target : docSessions.getOrDefault(docId, Set.of())) {
            if (!target.equals(source) && target.isOpen()) {
                synchronized (target) {
                    target.sendMessage(new TextMessage(payload));
                }
            }
        }
    }

    private void sendToAll(String docId, CollabMessage message) throws IOException {
        for (WebSocketSession target : docSessions.getOrDefault(docId, Set.of())) {
            send(target, message);
        }
    }

    private void send(WebSocketSession session, CollabMessage message) throws IOException {
        if (session.isOpen()) {
            synchronized (session) {
                session.sendMessage(new TextMessage(objectMapper.writeValueAsString(message)));
            }
        }
    }

    private Map<String, Object> sessionUser(WebSocketSession session) {
        Map<String, Object> user = new HashMap<>();
        user.put("userId", userId(session));
        user.put("username", username(session));
        return user;
    }

    private Long userId(WebSocketSession session) {
        Object value = session.getAttributes().get("userId");
        return value instanceof Long ? (Long) value : Long.valueOf(value.toString());
    }

    private String username(WebSocketSession session) {
        Object value = session.getAttributes().get("username");
        return value == null ? null : value.toString();
    }

    private String extractDocId(WebSocketSession session) {
        Object value = session.getAttributes().get("docId");
        if (value != null) {
            return value.toString();
        }
        URI uri = session.getUri();
        if (uri == null) {
            throw new IllegalArgumentException("Missing document id");
        }
        String path = uri.getPath();
        return path.substring(path.lastIndexOf('/') + 1);
    }

    @Data
    public static class CollabMessage {
        private String type;
        private String docId;
        private String sessionId;
        private Long userId;
        private String username;
        private Object data;
        private Long timestamp;
    }

    @Data
    public static class DocumentUpdatePayload {
        private String title;
        private String content;
        private Long revision;
    }
}
