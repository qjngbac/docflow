package com.docflow.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.docflow.common.BusinessException;
import com.docflow.common.ErrorCode;
import com.docflow.entity.FileAttachment;
import com.docflow.mapper.FileAttachmentMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@Service
public class AttachmentService {

    @Autowired private FileAttachmentMapper attachmentMapper;
    @Autowired private PermissionService permissionService;
    @Autowired private FileService fileService;

    public List<FileAttachment> list(Long docId, Long userId) {
        permissionService.requireReadable(docId, userId);
        return attachmentMapper.selectList(new LambdaQueryWrapper<FileAttachment>()
                .eq(FileAttachment::getDocId, docId)
                .orderByDesc(FileAttachment::getCreatedAt));
    }

    @Transactional
    public FileAttachment upload(Long docId, Long userId, MultipartFile file) {
        permissionService.requireWritable(docId, userId);
        String url = fileService.upload(file);
        FileAttachment attachment = new FileAttachment();
        attachment.setDocId(docId);
        attachment.setUploaderId(userId);
        attachment.setOriginalName(file.getOriginalFilename() == null ? "attachment" : file.getOriginalFilename());
        attachment.setFileUrl(url);
        attachment.setFileSize(file.getSize());
        attachment.setMimeType(file.getContentType());
        attachmentMapper.insert(attachment);
        return attachment;
    }

    @Transactional
    public void delete(Long docId, Long attachmentId, Long userId) {
        permissionService.requireWritable(docId, userId);
        FileAttachment attachment = attachmentMapper.selectById(attachmentId);
        if (attachment == null || !docId.equals(attachment.getDocId())) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "Attachment not found");
        }
        attachmentMapper.deleteById(attachmentId);
        fileService.deleteByUrl(attachment.getFileUrl());
    }

    public void deleteByDocument(Long docId) {
        List<FileAttachment> attachments = attachmentMapper.selectList(
                new LambdaQueryWrapper<FileAttachment>().eq(FileAttachment::getDocId, docId));
        attachments.forEach(item -> fileService.deleteByUrl(item.getFileUrl()));
        attachmentMapper.delete(new LambdaQueryWrapper<FileAttachment>().eq(FileAttachment::getDocId, docId));
    }
}
