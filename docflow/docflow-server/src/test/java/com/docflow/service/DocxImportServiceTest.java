package com.docflow.service;

import com.docflow.common.BusinessException;
import org.apache.poi.util.Units;
import org.apache.poi.xwpf.usermodel.Document;
import org.apache.poi.xwpf.usermodel.UnderlinePatterns;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DocxImportServiceTest {

    private static final String DOCX_MIME =
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
    private static final byte[] PIXEL_PNG = Base64.getDecoder().decode(
            "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mNk+A8AAQUBAScY42YAAAAASUVORK5CYII=");

    @Mock private FileService fileService;
    private DocxImportService service;

    @BeforeEach
    void setUp() {
        service = new DocxImportService();
        ReflectionTestUtils.setField(service, "fileService", fileService);
        ReflectionTestUtils.setField(service, "htmlSanitizer", new HtmlSanitizer());
    }

    @Test
    void importsRichDocxAndEmbeddedImage() throws Exception {
        byte[] bytes;
        try (XWPFDocument document = new XWPFDocument();
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            XWPFParagraph heading = document.createParagraph();
            heading.setStyle("Heading1");
            heading.createRun().setText("Project title");

            XWPFParagraph paragraph = document.createParagraph();
            XWPFRun rich = paragraph.createRun();
            rich.setBold(true); rich.setItalic(true); rich.setUnderline(UnderlinePatterns.SINGLE);
            rich.setStrikeThrough(true); rich.setText("formatted");
            paragraph.createHyperlinkRun("https://example.com").setText("link");

            XWPFRun image = document.createParagraph().createRun();
            image.addPicture(new ByteArrayInputStream(PIXEL_PNG), Document.PICTURE_TYPE_PNG,
                    "pixel.png", Units.toEMU(10), Units.toEMU(10));

            var table = document.createTable(1, 2);
            table.getRow(0).getCell(0).setText("A");
            table.getRow(0).getCell(1).setText("B");
            document.write(output);
            bytes = output.toByteArray();
        }
        when(fileService.storeBytes(any(), anyString(), anyString())).thenReturn("/files/docx-images/pixel.png");

        DocxImportService.ImportedDocx imported = service.convert(file("normal.docx", bytes));

        assertThat(imported.title()).isEqualTo("normal");
        assertThat(imported.html()).contains("<h1>Project title</h1>", "<strong>", "<em>", "<u>", "<s>");
        assertThat(imported.html()).contains("href=\"https://example.com\"", "<table>", "/files/docx-images/pixel.png");
    }

    @Test
    void rejectsDocxWithoutImportableContent() throws Exception {
        assertThatThrownBy(() -> service.convert(file("empty.docx", documentBytes(null))))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("no importable content");
    }

    @Test
    void discardsEmptyWordParagraphsUsedForVisualSpacing() throws Exception {
        byte[] bytes;
        try (XWPFDocument document = new XWPFDocument();
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            document.createParagraph().createRun().setText("First paragraph");
            document.createParagraph();
            document.createParagraph().createRun().setText("Second paragraph");
            document.write(output);
            bytes = output.toByteArray();
        }

        DocxImportService.ImportedDocx imported = service.convert(file("spacing.docx", bytes));

        assertThat(imported.html()).contains("<p>First paragraph</p>", "<p>Second paragraph</p>");
        assertThat(imported.html()).doesNotContain("<p><br></p>");
    }

    @Test
    void rejectsDamagedDocx() {
        assertThatThrownBy(() -> service.convert(file("damaged.docx", "not-a-zip".getBytes())))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("signature");
    }

    @Test
    void rejectsDamagedLegacyDocWithClearBusinessError() {
        MockMultipartFile legacy = new MockMultipartFile(
                "file", "damaged.doc", "application/msword", "not-an-ole-file".getBytes());

        assertThatThrownBy(() -> service.convert(legacy))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("signature");
    }

    @Test
    void rejectsUnexpectedWordMimeType() {
        MockMultipartFile file = new MockMultipartFile(
                "file", "document.doc", "text/html", new byte[]{1, 2, 3});

        assertThatThrownBy(() -> service.convert(file))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("MIME");
    }

    @Test
    void rejectsOversizedDocxBeforeReadingIt() {
        MultipartFile file = mock(MultipartFile.class);
        when(file.isEmpty()).thenReturn(false);
        when(file.getSize()).thenReturn(DocxImportService.MAX_DOCX_SIZE + 1);

        assertThatThrownBy(() -> service.convert(file))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("10 MB");
    }

    @Test
    void escapesDangerousTextFromDocx() throws Exception {
        DocxImportService.ImportedDocx imported = service.convert(file(
                "unsafe.docx", documentBytes("<script>alert('x')</script><b onclick='x'>text</b>")));

        org.jsoup.nodes.Document parsed = org.jsoup.Jsoup.parseBodyFragment(imported.html());
        assertThat(parsed.select("script,[onclick]")).isEmpty();
        assertThat(imported.html()).contains("&lt;script&gt;");
    }

    private MockMultipartFile file(String name, byte[] bytes) {
        return new MockMultipartFile("file", name, DOCX_MIME, bytes);
    }

    private byte[] documentBytes(String text) throws Exception {
        try (XWPFDocument document = new XWPFDocument();
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            if (text != null) document.createParagraph().createRun().setText(text);
            document.write(output);
            return output.toByteArray();
        }
    }
}
