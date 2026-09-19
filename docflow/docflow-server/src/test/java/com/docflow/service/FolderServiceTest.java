package com.docflow.service;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.docflow.entity.Document;
import com.docflow.entity.Folder;
import com.docflow.mapper.DocumentMapper;
import com.docflow.mapper.FolderMapper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FolderServiceTest {

    @Mock private FolderMapper folderMapper;
    @Mock private DocumentMapper documentMapper;
    @InjectMocks private FolderService folderService;

    @BeforeAll
    static void registerEntitiesForLambdaWrappers() {
        // 单测没有 Spring 容器，MyBatis-Plus 的 lambda→列名缓存需要显式初始化，
        // 否则构造 LambdaUpdateWrapper（.eq/.set 里的方法引用）会抛 "can not find lambda cache for this entity"。
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
        TableInfoHelper.initTableInfo(assistant, Document.class);
        TableInfoHelper.initTableInfo(assistant, Folder.class);
    }

    private static Folder folder(long id, long parentId, int deleted) {
        Folder folder = new Folder();
        folder.setId(id);
        folder.setParentId(parentId);
        folder.setOwnerId(9L);
        folder.setIsDeleted(deleted);
        return folder;
    }

    @Test
    void deletingAFolderMovesContentsToItsParentAndOnlyHidesThatFolder() {
        when(folderMapper.selectById(50L)).thenReturn(folder(50L, 40L, 0));
        when(folderMapper.selectById(40L)).thenReturn(folder(40L, 0L, 0));

        folderService.delete(50L, 9L);

        ArgumentCaptor<Folder> updated = ArgumentCaptor.forClass(Folder.class);
        verify(folderMapper).updateById(updated.capture());
        assertThat(updated.getValue().getId()).isEqualTo(50L);
        assertThat(updated.getValue().getIsDeleted()).isEqualTo(1);
        // 文档与直接子文件夹都要被上提，而不是留在指向已删除文件夹的悬空状态。
        verify(documentMapper).update(isNull(), any());
        verify(folderMapper).update(isNull(), any());
    }

    @Test
    void ancestorResolutionSkipsAncestorsThatWereAlreadyDeleted() {
        // 父级已被删除时必须继续上溯，否则会把内容挂到另一个已删除文件夹上、造出新的悬空引用。
        when(folderMapper.selectById(40L)).thenReturn(folder(40L, 30L, 1));
        when(folderMapper.selectById(30L)).thenReturn(folder(30L, 0L, 0));

        assertThat(folderService.resolveLiveAncestor(40L, 9L)).isEqualTo(30L);
    }

    @Test
    void ancestorResolutionFallsBackToRootWhenEveryAncestorIsDeleted() {
        when(folderMapper.selectById(40L)).thenReturn(folder(40L, 0L, 1));

        assertThat(folderService.resolveLiveAncestor(40L, 9L)).isEqualTo(0L);
    }

    @Test
    void ancestorResolutionFallsBackToRootWhenAncestorIsMissing() {
        when(folderMapper.selectById(77L)).thenReturn(null);

        assertThat(folderService.resolveLiveAncestor(77L, 9L)).isEqualTo(0L);
    }
}
