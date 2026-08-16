package com.docflow.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.docflow.common.BusinessException;
import com.docflow.common.ErrorCode;
import com.docflow.dto.AttachmentUploadInitRequest;
import com.docflow.entity.AttachmentUploadSession;
import com.docflow.entity.FileAttachment;
import com.docflow.mapper.AttachmentUploadSessionMapper;
import com.docflow.mapper.FileAttachmentMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.UUID;

@Service
public class AttachmentUploadService {
    private static final long MAX_CHUNK_BYTES = 8L * 1024 * 1024;
    private final AttachmentUploadSessionMapper sessionMapper;
    private final FileAttachmentMapper attachmentMapper;
    private final PermissionService permissionService;
    private final FileService fileService;
    @Value("${file.upload-dir:./uploads}") private String uploadDir;

    public AttachmentUploadService(AttachmentUploadSessionMapper sessionMapper, FileAttachmentMapper attachmentMapper,
                                   PermissionService permissionService, FileService fileService) {
        this.sessionMapper = sessionMapper; this.attachmentMapper = attachmentMapper;
        this.permissionService = permissionService; this.fileService = fileService;
    }

    @Transactional
    public AttachmentUploadSession start(Long docId, Long userId, AttachmentUploadInitRequest request) {
        permissionService.requireWritable(docId, userId);
        fileService.validateAttachmentName(request.getFileName());
        AttachmentUploadSession session = new AttachmentUploadSession();
        session.setId(UUID.randomUUID().toString()); session.setDocId(docId); session.setUserId(userId);
        session.setOriginalName(request.getFileName().trim()); session.setMimeType(request.getMimeType());
        session.setTotalSize(request.getSize()); session.setTotalChunks(request.getTotalChunks()); session.setReceivedChunks(0);
        session.setSha256(request.getSha256() == null ? null : request.getSha256().toLowerCase()); session.setStatus("UPLOADING");
        session.setExpiresAt(LocalDateTime.now().plusHours(24)); session.setCreatedAt(LocalDateTime.now()); session.setUpdatedAt(session.getCreatedAt());
        sessionMapper.insert(session);
        return session;
    }

    @Transactional
    public AttachmentUploadSession uploadChunk(Long docId, String sessionId, int chunkIndex, Long userId, MultipartFile chunk) {
        AttachmentUploadSession session = requireSession(docId, sessionId, userId);
        if (chunkIndex < 0 || chunkIndex >= session.getTotalChunks()) throw new BusinessException(ErrorCode.BAD_REQUEST, "Chunk index is out of range");
        if (chunk == null || chunk.isEmpty()) throw new BusinessException(ErrorCode.BAD_REQUEST, "Chunk is empty");
        if (chunk.getSize() > MAX_CHUNK_BYTES) throw new BusinessException(ErrorCode.PAYLOAD_TOO_LARGE, "Chunk must not exceed 8 MB");
        Path target = sessionDirectory(session).resolve(chunkIndex + ".part");
        try {
            Files.createDirectories(target.getParent());
            boolean existed = Files.isRegularFile(target);
            chunk.transferTo(target);
            if (!existed) session.setReceivedChunks(countChunks(session));
            session.setUpdatedAt(LocalDateTime.now()); sessionMapper.updateById(session);
            return session;
        } catch (IOException exception) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "Chunk upload failed");
        }
    }

    @Transactional
    public FileAttachment complete(Long docId, String sessionId, Long userId) {
        AttachmentUploadSession session = requireSession(docId, sessionId, userId);
        if (countChunks(session) != session.getTotalChunks()) throw new BusinessException(ErrorCode.CONFLICT, "Not all upload chunks have arrived");
        Path combined = sessionDirectory(session).resolve("combined.upload");
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            long total = 0;
            try (OutputStream output = Files.newOutputStream(combined, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)) {
                for (int index = 0; index < session.getTotalChunks(); index++) {
                    Path chunk = sessionDirectory(session).resolve(index + ".part");
                    try (InputStream input = Files.newInputStream(chunk)) {
                        byte[] buffer = new byte[8192]; int read;
                        while ((read = input.read(buffer)) != -1) { output.write(buffer, 0, read); digest.update(buffer, 0, read); total += read; }
                    }
                }
            }
            if (total != session.getTotalSize()) throw new BusinessException(ErrorCode.CONFLICT, "Uploaded file size does not match the upload session");
            if (session.getSha256() != null && !session.getSha256().equalsIgnoreCase(HexFormat.of().formatHex(digest.digest()))) {
                throw new BusinessException(ErrorCode.CONFLICT, "Uploaded file checksum does not match the upload session");
            }
            String url = fileService.storeCompletedUpload(combined, session.getOriginalName());
            FileAttachment attachment = new FileAttachment();
            attachment.setDocId(docId); attachment.setUploaderId(userId); attachment.setOriginalName(session.getOriginalName());
            attachment.setFileUrl(url); attachment.setFileSize(total); attachment.setMimeType(session.getMimeType()); attachmentMapper.insert(attachment);
            session.setStatus("COMPLETE"); session.setUpdatedAt(LocalDateTime.now()); sessionMapper.updateById(session); deleteDirectory(sessionDirectory(session));
            return attachment;
        } catch (BusinessException exception) { throw exception;
        } catch (Exception exception) { throw new BusinessException(ErrorCode.INTERNAL_ERROR, "Upload assembly failed"); }
    }

    @Scheduled(cron = "0 15 * * * ?")
    public void removeExpiredUploads() {
        for (AttachmentUploadSession session : sessionMapper.selectList(new LambdaQueryWrapper<AttachmentUploadSession>()
                .eq(AttachmentUploadSession::getStatus, "UPLOADING").lt(AttachmentUploadSession::getExpiresAt, LocalDateTime.now()))) {
            session.setStatus("ABORTED"); sessionMapper.updateById(session); deleteDirectory(sessionDirectory(session));
        }
    }

    private AttachmentUploadSession requireSession(Long docId, String id, Long userId) {
        permissionService.requireWritable(docId, userId);
        AttachmentUploadSession session = sessionMapper.selectById(id);
        if (session == null || !docId.equals(session.getDocId()) || !userId.equals(session.getUserId())) throw new BusinessException(ErrorCode.NOT_FOUND, "Upload session not found");
        if (!"UPLOADING".equals(session.getStatus()) || session.getExpiresAt().isBefore(LocalDateTime.now())) throw new BusinessException(ErrorCode.CONFLICT, "Upload session has expired");
        return session;
    }
    private Path sessionDirectory(AttachmentUploadSession session) { return Path.of(uploadDir).toAbsolutePath().normalize().resolve(".chunks").resolve(session.getId()); }
    private int countChunks(AttachmentUploadSession session) { try (var paths = Files.list(sessionDirectory(session))) { return (int) paths.filter(path -> path.getFileName().toString().endsWith(".part")).count(); } catch (IOException ignored) { return 0; } }
    private void deleteDirectory(Path directory) { try { if (!Files.exists(directory)) return; try (var paths = Files.walk(directory)) { paths.sorted(Comparator.reverseOrder()).forEach(path -> { try { Files.deleteIfExists(path); } catch (IOException ignored) {} }); } } catch (IOException ignored) {} }
}
