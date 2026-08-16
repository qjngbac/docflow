package com.docflow.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.docflow.common.BusinessException;
import com.docflow.common.ErrorCode;
import com.docflow.entity.DocumentTemplate;
import com.docflow.mapper.DocumentTemplateMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;

@Service
public class TemplateService {
    @Autowired private DocumentTemplateMapper templateMapper;

    public List<DocumentTemplate> list(Long userId) {
        return templateMapper.selectList(new LambdaQueryWrapper<DocumentTemplate>()
                .isNull(DocumentTemplate::getOwnerId).or().eq(DocumentTemplate::getOwnerId, userId)
                .orderByAsc(DocumentTemplate::getOwnerId).orderByAsc(DocumentTemplate::getName));
    }

    public DocumentTemplate getReadable(Long id, Long userId) {
        DocumentTemplate template = templateMapper.selectById(id);
        if (template == null || (template.getOwnerId() != null && !template.getOwnerId().equals(userId))) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "Template not found");
        }
        return template;
    }

    public DocumentTemplate create(Long userId, DocumentTemplate request) {
        if (!StringUtils.hasText(request.getName()) || request.getName().length() > 100) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "Template name is required");
        }
        DocumentTemplate template = new DocumentTemplate();
        template.setOwnerId(userId);
        template.setName(request.getName().trim());
        template.setDescription(request.getDescription());
        template.setCategory(request.getCategory());
        template.setContent(request.getContent() == null ? "" : request.getContent());
        template.setContentFormat("HTML".equals(request.getContentFormat()) ? "HTML" : "MARKDOWN");
        templateMapper.insert(template);
        return template;
    }

    public void delete(Long id, Long userId) {
        DocumentTemplate template = templateMapper.selectById(id);
        if (template == null || template.getOwnerId() == null || !template.getOwnerId().equals(userId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "Cannot delete this template");
        }
        templateMapper.deleteById(id);
    }
}
