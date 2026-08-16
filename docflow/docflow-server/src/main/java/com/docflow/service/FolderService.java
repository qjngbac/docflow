package com.docflow.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.docflow.common.BusinessException;
import com.docflow.common.ErrorCode;
import com.docflow.dto.FolderRequest;
import com.docflow.entity.Folder;
import com.docflow.mapper.FolderMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class FolderService {

    @Autowired
    private FolderMapper folderMapper;

    public List<Folder> list(Long userId) {
        return folderMapper.selectList(new LambdaQueryWrapper<Folder>()
                .eq(Folder::getOwnerId, userId)
                .eq(Folder::getIsDeleted, 0)
                .orderByAsc(Folder::getParentId)
                .orderByAsc(Folder::getName));
    }

    public Folder create(Long userId, FolderRequest request) {
        validateParent(null, request.getParentId(), userId);
        Folder folder = new Folder();
        folder.setName(request.getName());
        folder.setParentId(request.getParentId() == null ? 0L : request.getParentId());
        folder.setOwnerId(userId);
        folder.setIsDeleted(0);
        folderMapper.insert(folder);
        return folder;
    }

    public Folder update(Long id, Long userId, FolderRequest request) {
        Folder folder = requireOwnerFolder(id, userId);
        validateParent(id, request.getParentId(), userId);
        folder.setName(request.getName());
        folder.setParentId(request.getParentId() == null ? 0L : request.getParentId());
        folderMapper.updateById(folder);
        return folder;
    }

    public void delete(Long id, Long userId) {
        Folder folder = requireOwnerFolder(id, userId);
        folder.setIsDeleted(1);
        folderMapper.updateById(folder);
    }

    private Folder requireOwnerFolder(Long id, Long userId) {
        Folder folder = folderMapper.selectById(id);
        if (folder == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "Folder not found");
        }
        if (!folder.getOwnerId().equals(userId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "No folder permission");
        }
        return folder;
    }

    private void validateParent(Long folderId, Long parentId, Long userId) {
        if (parentId == null || parentId == 0) return;
        if (parentId.equals(folderId)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "Folder cannot be its own parent");
        }
        Folder parent = requireOwnerFolder(parentId, userId);
        while (parent.getParentId() != null && parent.getParentId() != 0) {
            if (parent.getParentId().equals(folderId)) {
                throw new BusinessException(ErrorCode.BAD_REQUEST, "Folder cannot be moved into its descendant");
            }
            parent = requireOwnerFolder(parent.getParentId(), userId);
        }
    }
}
