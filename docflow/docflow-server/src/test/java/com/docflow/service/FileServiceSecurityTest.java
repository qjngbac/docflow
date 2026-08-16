package com.docflow.service;

import com.docflow.common.BusinessException;
import com.docflow.security.MalwareScanner;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import java.io.InputStream;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class FileServiceSecurityTest {
    private final MalwareScanner scanner = mock(MalwareScanner.class);
    private final FileService service = new FileService(scanner);

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
}
