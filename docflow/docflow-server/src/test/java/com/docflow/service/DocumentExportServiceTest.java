package com.docflow.service;

import com.docflow.entity.Document;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.ByteArrayInputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DocumentExportServiceTest {
    @Mock private PermissionService permissionService;
    private DocumentExportService service;

    @BeforeEach
    void setUp() {
        service = new DocumentExportService();
        ReflectionTestUtils.setField(service, "permissionService", permissionService);
        ReflectionTestUtils.setField(service, "htmlSanitizer", new HtmlSanitizer());
    }

    @Test
    void exportsRichTextTableAndFormulaToReadableDocx() throws Exception {
        Document document = new Document();
        document.setId(4L);
        document.setTitle("Project Notes");
        document.setContentFormat("HTML");
        when(permissionService.requireReadable(4L, 2L)).thenReturn(document);
        String html = "<h2>Summary</h2><p><strong>Important</strong> value "
                + "<span data-type='inline-math' data-latex='E=mc^2'></span></p>"
                + "<table><tr><th>Name</th><th>Value</th></tr><tr><td>A</td><td>B</td></tr></table>";

        var exported = service.exportDocx(4L, 2L, html);

        assertThat(exported.fileName()).isEqualTo("Project Notes.docx");
        try (XWPFDocument word = new XWPFDocument(new ByteArrayInputStream(exported.content()))) {
            String text = word.getParagraphs().stream().map(paragraph -> paragraph.getText())
                    .reduce("", (left, right) -> left + " " + right);
            String tableText = word.getTables().stream().map(table -> table.getText())
                    .reduce("", (left, right) -> left + " " + right);
            assertThat(text).contains("Project Notes", "Summary", "Important", "Formula: E=mc^2");
            assertThat(tableText).contains("Name", "Value", "A", "B");
        }
    }
}
