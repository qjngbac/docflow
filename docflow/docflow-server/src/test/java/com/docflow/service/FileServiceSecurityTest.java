package com.docflow.service;

import com.docflow.common.BusinessException;
import com.docflow.security.MalwareScanner;
import com.docflow.security.UserContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.assertj.core.api.Assertions.assertThat;

class FileServiceSecurityTest {
    @TempDir Path directory;
    private final MalwareScanner scanner = mock(MalwareScanner.class);
    private final FileService service = new FileService(scanner);

    @AfterEach
    void clearContext() {
        UserContext.clear();
        if (TransactionSynchronizationManager.isSynchronizationActive()) TransactionSynchronizationManager.clearSynchronization();
    }

    @Test
    void rejectsExecutableContentDisguisedAsAnImage() {
        MockMultipartFile file = new MockMultipartFile(
                "file", "photo.png", "image/png", new byte[]{'M', 'Z', 0, 0, 0, 0});

        assertThatThrownBy(() -> service.inspect(file))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("可执行文件");
    }

    @Test
    void scansFilesWhoseExtensionAndSignatureAgree() {
        byte[] png = new byte[]{(byte) 0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a, 0, 0, 0, 0};
        MockMultipartFile file = new MockMultipartFile("file", "photo.png", "image/png", png);

        service.inspect(file);

        verify(scanner).scan(any(InputStream.class), eq("photo.png"));
    }

    @Test
    void registersUploadedFileOwnershipAndRemovesFreshBytesWhenTransactionRollsBack() throws Exception {
        FileAccessService access = mock(FileAccessService.class);
        FileService stored = new FileService(scanner, access);
        ReflectionTestUtils.setField(stored, "uploadDir", directory.toString());
        UserContext.set(7L, "uploader");
        TransactionSynchronizationManager.initSynchronization();
        MockMultipartFile file = new MockMultipartFile("file", "note.txt", "text/plain", "hello".getBytes());

        String url = stored.upload(file);

        verify(access).register(url, 7L, "DOCUMENT");
        Path physical = directory.resolve(url.substring(7));
        assertThat(Files.exists(physical)).isTrue();
        for (var synchronization : TransactionSynchronizationManager.getSynchronizations()) {
            synchronization.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK);
        }
        assertThat(Files.exists(physical)).isFalse();
    }

    @Test
    void committedCleanupDeletesOnlyAfterRegistryClaimsNoReferences() throws Exception {
        FileAccessService access = mock(FileAccessService.class);
        FileService stored = new FileService(scanner, access);
        ReflectionTestUtils.setField(stored, "uploadDir", directory.toString());
        Path physical = directory.resolve("shared.txt");
        Files.writeString(physical, "shared bytes");
        TransactionSynchronizationManager.initSynchronization();

        stored.deleteAfterCommit("/files/shared.txt");
        assertThat(Files.exists(physical)).isTrue();
        for (var synchronization : TransactionSynchronizationManager.getSynchronizations()) synchronization.afterCommit();
        assertThat(Files.exists(physical)).isTrue();
        when(access.claimDeletionAfterCommit("/files/shared.txt")).thenReturn(true);
        for (var synchronization : TransactionSynchronizationManager.getSynchronizations()) synchronization.afterCommit();
        assertThat(Files.exists(physical)).isFalse();
    }

    @Test
    void rolledBackDocumentDeletionDoesNotDeletePhysicalFiles() throws Exception {
        FileAccessService access = mock(FileAccessService.class);
        FileService stored = new FileService(scanner, access);
        ReflectionTestUtils.setField(stored, "uploadDir", directory.toString());
        Path physical = directory.resolve("retained.txt");
        Files.writeString(physical, "retained bytes");
        TransactionSynchronizationManager.initSynchronization();
        stored.deleteAfterCommit("/files/retained.txt");

        for (var synchronization : TransactionSynchronizationManager.getSynchronizations()) {
            synchronization.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK);
        }

        assertThat(Files.exists(physical)).isTrue();
        org.mockito.Mockito.verifyNoInteractions(access);
    }
}
