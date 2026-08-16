package com.docflow.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.docflow.entity.DocumentTag;
import com.docflow.entity.Tag;
import com.docflow.mapper.DocumentTagMapper;
import com.docflow.mapper.TagMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.*;

@Service
public class TagService {
    @Autowired private TagMapper tagMapper;
    @Autowired private DocumentTagMapper documentTagMapper;
    @Autowired private PermissionService permissionService;

    public List<Tag> list(Long userId) {
        return tagMapper.selectList(new LambdaQueryWrapper<Tag>()
                .eq(Tag::getOwnerId, userId).orderByAsc(Tag::getName));
    }

    public List<Tag> listForDocument(Long docId, Long userId) {
        permissionService.requireReadable(docId, userId);
        List<Long> ids = documentTagMapper.selectList(new LambdaQueryWrapper<DocumentTag>()
                .eq(DocumentTag::getDocId, docId)).stream().map(DocumentTag::getTagId).toList();
        return ids.isEmpty() ? List.of() : tagMapper.selectBatchIds(ids);
    }

    @Transactional
    public List<Tag> setForDocument(Long docId, Long userId, List<String> names) {
        permissionService.requireWritable(docId, userId);
        documentTagMapper.delete(new LambdaQueryWrapper<DocumentTag>().eq(DocumentTag::getDocId, docId));
        LinkedHashSet<String> normalized = new LinkedHashSet<>();
        if (names != null) names.stream().filter(StringUtils::hasText)
                .map(String::trim).filter(name -> name.length() <= 50).limit(10).forEach(normalized::add);
        for (String name : normalized) {
            Tag tag = tagMapper.selectOne(new LambdaQueryWrapper<Tag>()
                    .eq(Tag::getOwnerId, userId).eq(Tag::getName, name));
            if (tag == null) {
                tag = new Tag(); tag.setOwnerId(userId); tag.setName(name); tag.setColor("#64748b");
                tagMapper.insert(tag);
            }
            DocumentTag relation = new DocumentTag(); relation.setDocId(docId); relation.setTagId(tag.getId());
            documentTagMapper.insert(relation);
        }
        return listForDocument(docId, userId);
    }

    public void deleteRelations(Long docId) {
        documentTagMapper.delete(new LambdaQueryWrapper<DocumentTag>().eq(DocumentTag::getDocId, docId));
    }
}
