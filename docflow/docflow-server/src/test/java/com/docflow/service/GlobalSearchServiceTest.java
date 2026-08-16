package com.docflow.service;

import com.docflow.common.BusinessException;
import com.docflow.entity.Document;
import com.docflow.entity.Folder;
import com.docflow.entity.User;
import com.docflow.mapper.DocumentMapper;
import com.docflow.mapper.FolderMapper;
import com.docflow.mapper.UserMapper;
import com.docflow.vo.GlobalSearchVO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GlobalSearchServiceTest {
    @Mock private DocumentMapper documentMapper;
    @Mock private FolderMapper folderMapper;
    @Mock private UserMapper userMapper;
    @Mock private PermissionService permissionService;
    @InjectMocks private GlobalSearchService service;

    @Test
    void returnsGroupedPublicSearchResults() {
        Document document = new Document();
        document.setId(11L); document.setTitle("协作方案"); document.setSummary("项目说明");
        Folder folder = new Folder();
        folder.setId(12L); folder.setName("项目资料"); folder.setParentId(0L);
        User user = new User();
        user.setId(13L); user.setUsername("reviewer"); user.setNickname("审阅人");
        when(permissionService.listAccessibleDocIds(2L)).thenReturn(List.of(11L));
        when(documentMapper.selectList(any())).thenReturn(List.of(document));
        when(folderMapper.selectList(any())).thenReturn(List.of(folder));
        when(userMapper.selectList(any())).thenReturn(List.of(user));

        GlobalSearchVO result = service.search(2L, "项目");

        assertThat(result.getDocuments()).extracting(GlobalSearchVO.Item::getTitle).containsExactly("协作方案");
        assertThat(result.getFolders()).extracting(GlobalSearchVO.Item::getTitle).containsExactly("项目资料");
        assertThat(result.getUsers()).extracting(GlobalSearchVO.Item::getSubtitle).containsExactly("用户名：reviewer");
    }

    @Test
    void rejectsBlankAndOversizedKeywords() {
        assertThatThrownBy(() -> service.search(2L, "  ")).isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> service.search(2L, "x".repeat(101))).isInstanceOf(BusinessException.class);
    }
}
