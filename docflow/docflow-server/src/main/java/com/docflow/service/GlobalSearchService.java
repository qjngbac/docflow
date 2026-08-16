package com.docflow.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.docflow.common.BusinessException;
import com.docflow.common.ErrorCode;
import com.docflow.entity.Document;
import com.docflow.entity.Folder;
import com.docflow.entity.User;
import com.docflow.mapper.DocumentMapper;
import com.docflow.mapper.FolderMapper;
import com.docflow.mapper.UserMapper;
import com.docflow.vo.GlobalSearchVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;

@Service
public class GlobalSearchService {
    private static final int RESULT_LIMIT = 8;

    @Autowired private DocumentMapper documentMapper;
    @Autowired private FolderMapper folderMapper;
    @Autowired private UserMapper userMapper;
    @Autowired private PermissionService permissionService;

    public GlobalSearchVO search(Long userId, String rawKeyword) {
        String keyword = rawKeyword == null ? "" : rawKeyword.trim();
        if (!StringUtils.hasText(keyword) || keyword.length() > 100) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "Search keyword must be 1 to 100 characters");
        }

        GlobalSearchVO result = new GlobalSearchVO();
        result.setDocuments(searchDocuments(userId, keyword));
        result.setFolders(searchFolders(userId, keyword));
        result.setUsers(searchUsers(keyword));
        return result;
    }

    private List<GlobalSearchVO.Item> searchDocuments(Long userId, String keyword) {
        List<Long> permittedDocIds = permissionService.listAccessibleDocIds(userId);
        LambdaQueryWrapper<Document> query = new LambdaQueryWrapper<Document>()
                .and(access -> {
                    access.eq(Document::getOwnerId, userId);
                    if (!permittedDocIds.isEmpty()) access.or().in(Document::getId, permittedDocIds);
                })
                .eq(Document::getIsDeleted, 0)
                .and(match -> match.like(Document::getTitle, keyword)
                        .or().like(Document::getSummary, keyword)
                        .or().like(Document::getContent, keyword))
                .orderByDesc(Document::getUpdatedAt)
                .last("LIMIT " + RESULT_LIMIT);
        return documentMapper.selectList(query).stream().map(document -> {
            GlobalSearchVO.Item item = new GlobalSearchVO.Item();
            item.setType("DOCUMENT");
            item.setId(document.getId());
            item.setTitle(document.getTitle());
            item.setSubtitle(StringUtils.hasText(document.getSummary()) ? document.getSummary() : "暂无正文摘要");
            item.setParentId(document.getFolderId());
            item.setUpdatedAt(document.getUpdatedAt());
            return item;
        }).toList();
    }

    private List<GlobalSearchVO.Item> searchFolders(Long userId, String keyword) {
        return folderMapper.selectList(new LambdaQueryWrapper<Folder>()
                .eq(Folder::getOwnerId, userId)
                .eq(Folder::getIsDeleted, 0)
                .like(Folder::getName, keyword)
                .orderByAsc(Folder::getName)
                .last("LIMIT " + RESULT_LIMIT)).stream().map(folder -> {
            GlobalSearchVO.Item item = new GlobalSearchVO.Item();
            item.setType("FOLDER");
            item.setId(folder.getId());
            item.setTitle(folder.getName());
            item.setSubtitle(folder.getParentId() == null || folder.getParentId() == 0 ? "根目录下的文件夹" : "子文件夹");
            item.setParentId(folder.getParentId());
            item.setUpdatedAt(folder.getUpdatedAt());
            return item;
        }).toList();
    }

    private List<GlobalSearchVO.Item> searchUsers(String keyword) {
        return userMapper.selectList(new LambdaQueryWrapper<User>()
                .eq(User::getStatus, 1)
                .and(match -> match.like(User::getUsername, keyword).or().like(User::getNickname, keyword))
                .orderByAsc(User::getNickname)
                .last("LIMIT " + RESULT_LIMIT)).stream().map(user -> {
            GlobalSearchVO.Item item = new GlobalSearchVO.Item();
            item.setType("USER");
            item.setId(user.getId());
            item.setTitle(StringUtils.hasText(user.getNickname()) ? user.getNickname() : user.getUsername());
            item.setSubtitle("用户名：" + user.getUsername());
            item.setAvatar(user.getAvatar());
            item.setUpdatedAt(user.getUpdatedAt());
            return item;
        }).toList();
    }
}
