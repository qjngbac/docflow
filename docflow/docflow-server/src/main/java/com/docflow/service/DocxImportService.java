package com.docflow.service;

import com.docflow.common.BusinessException;
import com.docflow.common.ErrorCode;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.hwpf.HWPFDocument;
import org.apache.poi.hwpf.converter.PicturesManager;
import org.apache.poi.hwpf.converter.WordToHtmlConverter;
import org.apache.poi.hwpf.usermodel.PictureType;
import org.apache.poi.openxml4j.util.ZipSecureFile;
import org.apache.poi.xwpf.usermodel.IBodyElement;
import org.apache.poi.xwpf.usermodel.UnderlinePatterns;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFHyperlink;
import org.apache.poi.xwpf.usermodel.XWPFHyperlinkRun;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFPicture;
import org.apache.poi.xwpf.usermodel.XWPFPictureData;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.apache.poi.xwpf.usermodel.XWPFTableCell;
import org.apache.poi.xwpf.usermodel.XWPFTableRow;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Entities;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

@Slf4j
@Service
public class DocxImportService {

    static final long MAX_DOCX_SIZE = 10L * 1024 * 1024;
    private static final long MAX_UNCOMPRESSED_SIZE = 50L * 1024 * 1024;
    private static final int MAX_ZIP_ENTRIES = 2_000;
    private static final String DOCX_MIME = "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
    private static final String DOC_MIME = "application/msword";
    private static final Set<String> ACCEPTED_MIME_TYPES = Set.of(DOCX_MIME, DOC_MIME, "application/octet-stream");

    static {
        ZipSecureFile.setMinInflateRatio(0.01d);
        ZipSecureFile.setMaxEntrySize(MAX_UNCOMPRESSED_SIZE);
        ZipSecureFile.setMaxTextSize(10L * 1024 * 1024);
    }

    @Autowired private FileService fileService;
    @Autowired private HtmlSanitizer htmlSanitizer;

    public ImportedDocx convert(MultipartFile file) {
        validateMetadata(file);
        fileService.inspect(file);
        List<String> storedImages = new ArrayList<>();
        try {
            byte[] bytes = file.getBytes();
            boolean legacy = file.getOriginalFilename().toLowerCase(Locale.ROOT).endsWith(".doc");
            String html = legacy ? convertLegacyDoc(bytes, storedImages) : convertDocx(bytes, storedImages);
            html = htmlSanitizer.clean(html);
            org.jsoup.nodes.Document parsed = Jsoup.parseBodyFragment(html);
            if (parsed.text().isBlank() && parsed.select("img,table").isEmpty()) {
                throw new BusinessException(ErrorCode.BAD_REQUEST, "Word document has no importable content");
            }
            String title = file.getOriginalFilename().replaceFirst("(?i)\\.docx?$", "");
            return new ImportedDocx(StringUtils.hasText(title) ? title : "Imported document", html,
                    List.copyOf(storedImages));
        } catch (BusinessException e) {
            cleanup(storedImages);
            throw e;
        } catch (Exception e) {
            cleanup(storedImages);
            log.warn("Word import failed for {} ({})", safeFilename(file), e.getClass().getSimpleName());
            throw new BusinessException(ErrorCode.BAD_REQUEST, "Word file is damaged or unsupported");
        }
    }

    private String convertDocx(byte[] bytes, List<String> storedImages) throws IOException {
        validatePackage(bytes);
        try (XWPFDocument document = new XWPFDocument(new ByteArrayInputStream(bytes))) {
            return convertBody(document, storedImages);
        }
    }

    private String convertLegacyDoc(byte[] bytes, List<String> storedImages) throws Exception {
        validateLegacySignature(bytes);
        try (HWPFDocument document = new HWPFDocument(new ByteArrayInputStream(bytes))) {
            org.w3c.dom.Document target = DocumentBuilderFactory.newInstance()
                    .newDocumentBuilder().newDocument();
            WordToHtmlConverter converter = new WordToHtmlConverter(target);
            PicturesManager pictures = (content, pictureType, suggestedName, width, height) -> {
                String extension = legacyImageExtension(pictureType, suggestedName);
                if (extension == null) return null;
                String url = fileService.storeBytes(content, extension, "doc-images");
                storedImages.add(url);
                return url;
            };
            converter.setPicturesManager(pictures);
            converter.processDocument(document);
            Transformer transformer = TransformerFactory.newInstance().newTransformer();
            transformer.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "yes");
            transformer.setOutputProperty(OutputKeys.METHOD, "html");
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            transformer.transform(new DOMSource(converter.getDocument()), new StreamResult(output));
            return Jsoup.parse(output.toString(java.nio.charset.StandardCharsets.UTF_8)).body().html();
        }
    }

    private String convertBody(XWPFDocument document, List<String> storedImages) {
        StringBuilder html = new StringBuilder();
        String openList = null;
        for (IBodyElement element : document.getBodyElements()) {
            if (element instanceof XWPFParagraph paragraph && paragraph.getNumID() != null) {
                String listTag = isOrdered(paragraph) ? "ol" : "ul";
                if (!listTag.equals(openList)) {
                    if (openList != null) html.append("</").append(openList).append('>');
                    html.append('<').append(listTag).append('>');
                    openList = listTag;
                }
                html.append("<li>").append(renderInline(document, paragraph, storedImages)).append("</li>");
                continue;
            }
            if (openList != null) {
                html.append("</").append(openList).append('>');
                openList = null;
            }
            if (element instanceof XWPFParagraph paragraph) {
                html.append(renderParagraph(document, paragraph, storedImages));
            } else if (element instanceof XWPFTable table) {
                html.append(renderTable(document, table, storedImages));
            }
        }
        if (openList != null) html.append("</").append(openList).append('>');
        return html.toString();
    }

    private String renderParagraph(XWPFDocument document, XWPFParagraph paragraph,
                                   List<String> storedImages) {
        String style = styleName(document, paragraph).toLowerCase(Locale.ROOT);
        if (style.contains("code") || style.contains("preformatted")) {
            return "<pre><code>" + escape(paragraph.getText()) + "</code></pre>";
        }
        String content = renderInline(document, paragraph, storedImages);
        // Empty Word paragraphs are commonly used only for visual spacing. Page margins
        // belong to the editor layout, so importing those paragraphs creates fake blank areas.
        if (content.isBlank()) return "";
        int headingLevel = headingLevel(style);
        if (headingLevel > 0) return "<h" + headingLevel + ">" + content + "</h" + headingLevel + ">";
        if (style.contains("quote") || style.contains("引用")) {
            return "<blockquote><p>" + content + "</p></blockquote>";
        }
        return "<p>" + content + "</p>";
    }

    private String renderInline(XWPFDocument document, XWPFParagraph paragraph,
                                List<String> storedImages) {
        StringBuilder html = new StringBuilder();
        for (XWPFRun run : paragraph.getRuns()) {
            String text = escape(run.text()).replace("\r\n", "<br>").replace("\n", "<br>");
            if (!text.isEmpty()) {
                if (isCodeRun(document, run)) text = "<code>" + text + "</code>";
                if (run.isBold()) text = "<strong>" + text + "</strong>";
                if (run.isItalic()) text = "<em>" + text + "</em>";
                if (run.getUnderline() != UnderlinePatterns.NONE) text = "<u>" + text + "</u>";
                if (run.isStrikeThrough() || run.isDoubleStrikeThrough()) text = "<s>" + text + "</s>";
                if (run instanceof XWPFHyperlinkRun hyperlinkRun) {
                    XWPFHyperlink hyperlink = document.getHyperlinkByID(hyperlinkRun.getHyperlinkId());
                    if (hyperlink != null && isSafeLink(hyperlink.getURL())) {
                        text = "<a href=\"" + escapeAttribute(hyperlink.getURL())
                                + "\" target=\"_blank\" rel=\"noopener noreferrer\">" + text + "</a>";
                    }
                }
                html.append(text);
            }
            for (XWPFPicture picture : run.getEmbeddedPictures()) {
                XWPFPictureData pictureData = picture.getPictureData();
                String extension = normalizeImageExtension(pictureData.suggestFileExtension());
                if (extension == null) {
                    html.append("<span>[Unsupported image]</span>");
                    continue;
                }
                String url = fileService.storeBytes(pictureData.getData(), extension, "docx-images");
                storedImages.add(url);
                String description = picture.getDescription();
                html.append("<img src=\"").append(escapeAttribute(url)).append("\" alt=\"")
                        .append(escapeAttribute(description == null ? "" : description)).append("\">");
            }
        }
        return html.toString();
    }

    private String renderTable(XWPFDocument document, XWPFTable table,
                               List<String> storedImages) {
        StringBuilder html = new StringBuilder("<table><tbody>");
        for (XWPFTableRow row : table.getRows()) {
            html.append("<tr>");
            for (XWPFTableCell cell : row.getTableCells()) {
                StringBuilder cellHtml = new StringBuilder();
                for (XWPFParagraph paragraph : cell.getParagraphs()) {
                    cellHtml.append(renderParagraph(document, paragraph, storedImages));
                }
                if (cellHtml.length() == 0) cellHtml.append("<p><br></p>");
                html.append("<td>").append(cellHtml).append("</td>");
            }
            html.append("</tr>");
        }
        return html.append("</tbody></table>").toString();
    }

    private void validateMetadata(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "Word file is empty");
        }
        if (file.getSize() > MAX_DOCX_SIZE) {
            throw new BusinessException(ErrorCode.PAYLOAD_TOO_LARGE, "Word file must not exceed 10 MB");
        }
        String filename = file.getOriginalFilename();
        String lowerName = filename == null ? "" : filename.toLowerCase(Locale.ROOT);
        if (!StringUtils.hasText(filename) || !(lowerName.endsWith(".docx") || lowerName.endsWith(".doc"))) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "Only .doc and .docx files can be imported here");
        }
        String contentType = file.getContentType();
        if (StringUtils.hasText(contentType) && !ACCEPTED_MIME_TYPES.contains(contentType.toLowerCase(Locale.ROOT))) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "Invalid Word MIME type");
        }
    }

    private void validateLegacySignature(byte[] bytes) {
        byte[] signature = {(byte) 0xD0, (byte) 0xCF, 0x11, (byte) 0xE0,
                (byte) 0xA1, (byte) 0xB1, 0x1A, (byte) 0xE1};
        if (bytes.length < signature.length) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "DOC file signature is invalid");
        }
        for (int index = 0; index < signature.length; index++) {
            if (bytes[index] != signature[index]) {
                throw new BusinessException(ErrorCode.BAD_REQUEST, "DOC file signature is invalid");
            }
        }
    }

    private String legacyImageExtension(PictureType type, String suggestedName) {
        if (suggestedName != null && suggestedName.contains(".")) {
            String extension = normalizeImageExtension(
                    suggestedName.substring(suggestedName.lastIndexOf('.') + 1));
            if (extension != null) return extension;
        }
        return type == null ? null : normalizeImageExtension(type.getExtension());
    }

    private void validatePackage(byte[] bytes) throws IOException {
        if (bytes.length < 4 || bytes[0] != 'P' || bytes[1] != 'K') {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "DOCX file signature is invalid");
        }
        boolean contentTypes = false;
        boolean documentXml = false;
        long total = 0;
        int entries = 0;
        byte[] buffer = new byte[8192];
        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(bytes))) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                if (++entries > MAX_ZIP_ENTRIES) {
                    throw new BusinessException(ErrorCode.BAD_REQUEST, "DOCX archive contains too many entries");
                }
                if ("[Content_Types].xml".equals(entry.getName())) contentTypes = true;
                if ("word/document.xml".equals(entry.getName())) documentXml = true;
                int read;
                while ((read = zip.read(buffer)) != -1) {
                    total += read;
                    if (total > MAX_UNCOMPRESSED_SIZE) {
                        throw new BusinessException(ErrorCode.PAYLOAD_TOO_LARGE, "DOCX expanded content is too large");
                    }
                }
            }
        }
        if (!contentTypes || !documentXml) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "DOCX package is incomplete");
        }
    }

    private String styleName(XWPFDocument document, XWPFParagraph paragraph) {
        String styleId = paragraph.getStyle();
        if (!StringUtils.hasText(styleId)) return "";
        if (document.getStyles() != null && document.getStyles().getStyle(styleId) != null
                && document.getStyles().getStyle(styleId).getName() != null) {
            return document.getStyles().getStyle(styleId).getName();
        }
        return styleId;
    }

    private boolean isCodeRun(XWPFDocument document, XWPFRun run) {
        String styleId = run.getStyle();
        if (!StringUtils.hasText(styleId)) return false;
        String name = styleId;
        if (document.getStyles() != null && document.getStyles().getStyle(styleId) != null
                && document.getStyles().getStyle(styleId).getName() != null) {
            name = document.getStyles().getStyle(styleId).getName();
        }
        return name.toLowerCase(Locale.ROOT).contains("code");
    }

    private int headingLevel(String style) {
        for (int level = 1; level <= 6; level++) {
            if (style.contains("heading " + level) || style.contains("heading" + level)
                    || style.contains("标题 " + level) || style.contains("标题" + level)) return level;
        }
        return style.equals("title") || style.equals("标题") ? 1 : 0;
    }

    private boolean isOrdered(XWPFParagraph paragraph) {
        String format = paragraph.getNumFmt();
        return format != null && !Set.of("bullet", "none").contains(format.toLowerCase(Locale.ROOT));
    }

    private boolean isSafeLink(String url) {
        if (!StringUtils.hasText(url)) return false;
        String normalized = url.toLowerCase(Locale.ROOT);
        return normalized.startsWith("http://") || normalized.startsWith("https://")
                || normalized.startsWith("mailto:");
    }

    private String normalizeImageExtension(String extension) {
        if (extension == null) return null;
        String normalized = extension.toLowerCase(Locale.ROOT);
        if (normalized.equals("jpg") || normalized.equals("jpeg") || normalized.equals("png")
                || normalized.equals("gif") || normalized.equals("bmp")) return normalized;
        return null;
    }

    private String escape(String value) {
        return Entities.escape(value == null ? "" : value);
    }

    private String escapeAttribute(String value) {
        return Entities.escape(value == null ? "" : value, new org.jsoup.nodes.Document.OutputSettings());
    }

    private String safeFilename(MultipartFile file) {
        return file == null || file.getOriginalFilename() == null
                ? "unknown" : file.getOriginalFilename().replaceAll("[\\r\\n]", "_");
    }

    private void cleanup(List<String> urls) {
        for (String url : urls) {
            try {
                fileService.deleteByUrl(url);
            } catch (RuntimeException cleanupFailure) {
                log.warn("Failed to clean imported image {}", url);
            }
        }
    }

    public record ImportedDocx(String title, String html, List<String> imageUrls) {}
}
