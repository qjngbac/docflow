package com.docflow.controller;

import com.docflow.security.UserContext;
import com.docflow.service.FileAccessService;
import com.docflow.service.FileService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class StoredFileControllerTest {
    @TempDir Path directory;
    private final FileService files = mock(FileService.class);
    private final FileAccessService access = mock(FileAccessService.class);
    private final MockMvc mvc = MockMvcBuilders.standaloneSetup(new StoredFileController(files, access)).build();

    @AfterEach void clearUser() { UserContext.clear(); }

    @Test
    void anonymousAndUnrelatedAuthenticatedUsersCannotReadPrivateStoredFiles() throws Exception {
        mvc.perform(get("/files/private.png")).andExpect(status().isNotFound());
        UserContext.set(2L, "unrelated");
        mvc.perform(get("/files/private.png")).andExpect(status().isNotFound());
        verify(access).canRead("/files/private.png", null);
        verify(access).canRead("/files/private.png", 2L);
        verifyNoInteractions(files);
    }

    @Test
    void authorizedReaderAndRegisteredPublicAvatarCanReadOriginalPhysicalPath() throws Exception {
        Path image = directory.resolve("image.png");
        byte[] bytes = new byte[]{1, 2, 3};
        Files.write(image, bytes);
        when(access.canRead("/files/image.png", 1L)).thenReturn(true);
        when(files.resolveStoredFile("/files/image.png")).thenReturn(Optional.of(image));
        UserContext.set(1L, "reader");
        mvc.perform(get("/files/image.png")).andExpect(status().isOk()).andExpect(content().bytes(bytes))
                .andExpect(header().string("Cache-Control", "no-store"));
        UserContext.clear();
        when(access.canRead("/files/avatar.png", null)).thenReturn(true);
        when(files.resolveStoredFile("/files/avatar.png")).thenReturn(Optional.of(image));
        mvc.perform(get("/files/avatar.png")).andExpect(status().isOk()).andExpect(content().bytes(bytes));
    }
}
