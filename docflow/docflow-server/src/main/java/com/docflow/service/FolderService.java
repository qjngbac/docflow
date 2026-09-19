package com.docflow.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.docflow.common.BusinessException;
import com.docflow.common.ErrorCode;
import com.docflow.dto.FolderRequest;
import com.docflow.entity.Document;
import com.docflow.entity.Folder;
import com.docflow.mapper.DocumentMapper;
import com.docflow.mapper.FolderMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** 管理用户私有文件夹树，并阻止循环移动和跨用户访问。 */
@Service
public class FolderService {

    @Autowired
    private FolderMapper folderMapper;

    @Autowired
    private DocumentMapper documentMapper;

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

    /**
     * 删除文件夹采用"内容上提"语义：只隐藏这个文件夹本身，里面的文档与直接子文件夹
     * 全部移动到最近的未删除祖先（没有就落到根）。
     *
     * 这样不会产生"folder_id/parent_id 指向已删除文件夹"的悬空引用——否则子文件夹会在侧栏
     * 被当成孤儿提升到顶层、文档则不再属于任何可见文件夹。
     */
    @Transactional
    public void delete(Long id, Long userId) {
        Folder folder = requireOwnerFolder(id, userId);
        Long targetParent = resolveLiveAncestor(folder.getParentId(), userId);
        documentMapper.update(null, new LambdaUpdateWrapper<Document>()
                .eq(Document::getFolderId, id)
                .set(Document::getFolderId, targetParent));
        folderMapper.update(null, new LambdaUpdateWrapper<Folder>()
                .eq(Folder::getParentId, id)
                .set(Folder::getParentId, targetParent));
        folder.setIsDeleted(1);
        folderMapper.updateById(folder);
    }

    /**
     * 沿 parent 链向上找第一个仍未删除的祖先。
     * 父级本身已被删除（或不存在）时必须继续上溯，否则会把内容挂到另一个已删除文件夹上、造出新的悬空引用。
     * seen 集合用于防御历史脏数据造成的环。
     */
    /** 包级可见以便单测直接覆盖"上溯"这段分支逻辑。 */
    Long resolveLiveAncestor(Long parentId, Long userId) {
        Long current = parentId == null ? 0L : parentId;
        Set<Long> seen = new HashSet<>();
        while (current != null && current != 0L && seen.add(current)) {
            Folder parent = folderMapper.selectById(current);
            if (parent == null) {
                return 0L;
            }
            if (Integer.valueOf(0).equals(parent.getIsDeleted()) && parent.getOwnerId().equals(userId)) {
                return current;
            }
            current = parent.getParentId() == null ? 0L : parent.getParentId();
        }
        return 0L;
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
