package com.docflow.service;

import com.openhtmltopdf.pdfboxout.PdfRendererBuilder;
import com.docflow.entity.Document;
import org.apache.poi.util.Units;
import org.apache.poi.xwpf.usermodel.BreakType;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.apache.poi.xwpf.usermodel.XWPFTableCell;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Element;
import org.jsoup.nodes.Node;
import org.jsoup.nodes.TextNode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.List;
import java.util.Locale;

@Service
public class DocumentExportService {
    @Autowired private PermissionService permissionService;
    @Autowired private HtmlSanitizer htmlSanitizer;
    @Autowired private FileService fileService;
    @Value("${export.font-path:}") private String exportFontPath;

    public ExportedDocument exportDocx(Long docId, Long userId, String currentHtml) {
        Document document = permissionService.requireReadable(docId, userId);
        String html = currentHtml == null ? document.getContent() : currentHtml;
        if (!"HTML".equals(document.getContentFormat()) && currentHtml == null) {
            html = "<pre>" + org.jsoup.nodes.Entities.escape(html == null ? "" : html) + "</pre>";
        }
        String safeHtml = htmlSanitizer.clean(html);
        try (XWPFDocument word = new XWPFDocument(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            configurePage(word, document);
            if (document.getCoverImage() != null) appendStandaloneImage(word, document.getCoverImage(), "Document cover");
            appendTitle(word, document.getTitle());
            Element body = Jsoup.parseBodyFragment(safeHtml).body();
            for (Element child : body.children()) {
                appendBlock(word, child);
            }
            word.write(output);
            return new ExportedDocument(safeFileName(document.getTitle()) + ".docx", output.toByteArray());
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to create DOCX export", exception);
        }
    }

    public ExportedDocument exportPdf(Long docId, Long userId, String currentHtml) {
        Document document = permissionService.requireReadable(docId, userId);
        String safeHtml = htmlSanitizer.clean(currentHtml == null ? document.getContent() : currentHtml);
        Element body = Jsoup.parseBodyFragment(safeHtml == null ? "" : safeHtml).body();
        inlineStoredImages(body);
        String cover = dataUri(document.getCoverImage()).map(uri ->
                "<img class=\"cover\" src=\"" + uri + "\" alt=\"\" />").orElse("");
        String pageSize = "LETTER".equals(document.getPageFormat()) ? "Letter" : "A4";
        String header = cssText(document.getPageHeader());
        String footer = cssText(document.getPageFooter());
        String html = "<!doctype html><html><head><meta charset=\"UTF-8\"/><style>"
                + "@page{size:" + pageSize + ";margin:" + margin(document.getMarginTop()) + "mm "
                + margin(document.getMarginRight()) + "mm " + margin(document.getMarginBottom()) + "mm "
                + margin(document.getMarginLeft()) + "mm;@top-center{content:'" + header
                + "';font-size:9pt;color:#64748b}@bottom-center{content:'" + footer
                + "  ' counter(page) ' / ' counter(pages);font-size:9pt;color:#64748b}}"
                + "body{font-family:'DocFlow Sans',sans-serif;color:#1f2937;font-size:11pt;line-height:1.65;overflow-wrap:anywhere}"
                + "h1.title{font-size:22pt;margin:0 0 18pt}.cover{width:210px;max-height:145px;object-fit:cover;margin:0 0 16pt}"
                + "img{max-width:100%;height:auto}table{width:100%;border-collapse:collapse;table-layout:fixed}th,td{padding:6pt;border:1px solid #94a3b8;vertical-align:top}"
                + "pre{padding:8pt;background:#f1f5f9;white-space:pre-wrap}.page-break{page-break-after:always}.comment-highlight{background:#fef3c7}"
                + "[data-change-type='insert']{text-decoration:underline;color:#15803d}[data-change-type='delete']{text-decoration:line-through;color:#b91c1c}"
                + "[data-font-family='sans']{font-family:'DocFlow Sans',sans-serif}[data-font-family='serif']{font-family:serif}[data-font-family='mono']{font-family:monospace}"
                + "[data-font-size='12']{font-size:9pt}[data-font-size='14']{font-size:10.5pt}[data-font-size='16']{font-size:12pt}[data-font-size='18']{font-size:13.5pt}[data-font-size='20']{font-size:15pt}[data-font-size='24']{font-size:18pt}[data-text-color='red']{color:#dc2626}[data-text-color='blue']{color:#2563eb}[data-text-color='green']{color:#15803d}[data-text-color='gray']{color:#64748b}[data-text-color='purple']{color:#7e22ce}"
                + "[data-line-height='1.25']{line-height:1.25}[data-line-height='1.5']{line-height:1.5}[data-line-height='1.75']{line-height:1.75}[data-line-height='2']{line-height:2}[data-text-align='center']{text-align:center}[data-text-align='right']{text-align:right}[data-text-align='justify']{text-align:justify}"
                + "</style></head><body>" + cover + "<h1 class=\"title\">"
                + org.jsoup.nodes.Entities.escape(document.getTitle()) + "</h1>" + body.html() + "</body></html>";
        org.jsoup.nodes.Document parsed = Jsoup.parse(html);
        parsed.outputSettings().syntax(org.jsoup.nodes.Document.OutputSettings.Syntax.xml).prettyPrint(false);
        try (ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            PdfRendererBuilder builder = new PdfRendererBuilder();
            Path font = resolveExportFont();
            if (font != null) builder.useFont(font.toFile(), "DocFlow Sans");
            builder.withHtmlContent(parsed.html(), null);
            builder.toStream(output);
            builder.run();
            return new ExportedDocument(safeFileName(document.getTitle()) + ".pdf", output.toByteArray());
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to create PDF export", exception);
        }
    }

    private void appendTitle(XWPFDocument word, String title) {
        XWPFParagraph paragraph = word.createParagraph();
        paragraph.setStyle("Title");
        XWPFRun run = paragraph.createRun();
        run.setBold(true);
        run.setFontSize(20);
        run.setText(title == null || title.isBlank() ? "Untitled Document" : title);
    }

    private void appendBlock(XWPFDocument word, Element element) {
        String tag = element.normalName();
        if (element.hasClass("page-break") || "page-break".equals(element.attr("data-type"))) {
            word.createParagraph().createRun().addBreak(BreakType.PAGE);
            return;
        }
        if (tag.matches("h[1-6]")) {
            XWPFParagraph paragraph = word.createParagraph();
            paragraph.setStyle("Heading" + tag.substring(1));
            appendInline(paragraph, element.childNodes(), new Style());
            return;
        }
        if ("table".equals(tag)) {
            appendTable(word, element);
            return;
        }
        if ("ul".equals(tag) || "ol".equals(tag)) {
            int index = 1;
            for (Element item : element.children()) {
                if (!"li".equals(item.normalName())) continue;
                XWPFParagraph paragraph = word.createParagraph();
                paragraph.createRun().setText("ol".equals(tag) ? (index++) + ". " : "• ");
                appendInline(paragraph, item.childNodes(), new Style());
            }
            return;
        }
        XWPFParagraph paragraph = word.createParagraph();
        if ("blockquote".equals(tag)) {
            paragraph.setIndentationLeft(360);
            paragraph.setBorderLeft(org.apache.poi.xwpf.usermodel.Borders.SINGLE);
        }
        if ("pre".equals(tag)) {
            XWPFRun run = paragraph.createRun();
            run.setFontFamily("Consolas");
            run.setText(element.wholeText());
        } else if (element.hasAttr("data-latex")) {
            String number = element.attr("data-equation-number");
            paragraph.createRun().setText("[Formula] " + element.attr("data-latex") + (number.isBlank() ? "" : " (" + number + ")"));
        } else {
            appendInline(paragraph, element.childNodes(), new Style());
        }
    }

    private void appendTable(XWPFDocument word, Element source) {
        List<Element> rows = source.select("tr");
        int columnCount = rows.stream().mapToInt(row -> row.select("th,td").size()).max().orElse(1);
        XWPFTable table = word.createTable(Math.max(rows.size(), 1), Math.max(columnCount, 1));
        for (int rowIndex = 0; rowIndex < rows.size(); rowIndex++) {
            List<Element> cells = rows.get(rowIndex).select("th,td");
            for (int columnIndex = 0; columnIndex < cells.size(); columnIndex++) {
                XWPFTableCell cell = table.getRow(rowIndex).getCell(columnIndex);
                cell.removeParagraph(0);
                XWPFParagraph paragraph = cell.addParagraph();
                Style style = new Style();
                style.bold = "th".equals(cells.get(columnIndex).normalName());
                style.apply(cells.get(columnIndex));
                appendInline(paragraph, cells.get(columnIndex).childNodes(), style);
            }
        }
    }

    private void appendInline(XWPFParagraph paragraph, List<Node> nodes, Style style) {
        for (Node node : nodes) {
            if (node instanceof TextNode textNode) {
                if (!textNode.getWholeText().isEmpty()) addRun(paragraph, textNode.getWholeText(), style);
                continue;
            }
            if (!(node instanceof Element element)) continue;
            String tag = element.normalName();
            if ("br".equals(tag)) {
                paragraph.createRun().addBreak();
                continue;
            }
            if ("img".equals(tag)) {
                if (!appendImage(paragraph.createRun(), element.attr("src"), element.attr("alt"))) {
                    addRun(paragraph, "[Image unavailable: " + element.attr("src") + "]", style);
                }
                continue;
            }
            if (element.hasAttr("data-latex")) {
                String number = element.attr("data-equation-number");
                addRun(paragraph, "[Formula: " + element.attr("data-latex") + "]" + (number.isBlank() ? "" : " (" + number + ")"), style);
                continue;
            }
            Style nested = style.copy();
            nested.apply(element);
            nested.bold |= tag.equals("strong") || tag.equals("b");
            nested.italic |= tag.equals("em") || tag.equals("i");
            nested.underline |= tag.equals("u") || tag.equals("a");
            nested.strike |= tag.equals("s") || tag.equals("strike") || tag.equals("del");
            nested.code |= tag.equals("code");
            appendInline(paragraph, element.childNodes(), nested);
            if (tag.equals("a") && element.hasAttr("href")) addRun(paragraph, " (" + element.attr("href") + ")", nested);
        }
    }

    private void addRun(XWPFParagraph paragraph, String text, Style style) {
        XWPFRun run = paragraph.createRun();
        run.setBold(style.bold);
        run.setItalic(style.italic);
        run.setUnderline(style.underline ? org.apache.poi.xwpf.usermodel.UnderlinePatterns.SINGLE
                : org.apache.poi.xwpf.usermodel.UnderlinePatterns.NONE);
        run.setStrikeThrough(style.strike);
        if (style.code) run.setFontFamily("Consolas");
        if (style.fontFamily != null) run.setFontFamily(style.fontFamily);
        if (style.fontSize != null) run.setFontSize(style.fontSize);
        if (style.color != null) run.setColor(style.color);
        run.setText(text);
    }

    private String safeFileName(String title) {
        String safe = (title == null ? "document" : title).replaceAll("[\\\\/:*?\"<>|]", "_").trim();
        return safe.isEmpty() ? "document" : safe;
    }

    private void configurePage(XWPFDocument word, Document document) {
        var section = word.getDocument().getBody().addNewSectPr();
        var size = section.addNewPgSz();
        boolean letter = "LETTER".equals(document.getPageFormat());
        size.setW(java.math.BigInteger.valueOf(letter ? 12240 : 11906));
        size.setH(java.math.BigInteger.valueOf(letter ? 15840 : 16838));
        var margins = section.addNewPgMar();
        margins.setTop(java.math.BigInteger.valueOf(mmToTwips(margin(document.getMarginTop()))));
        margins.setRight(java.math.BigInteger.valueOf(mmToTwips(margin(document.getMarginRight()))));
        margins.setBottom(java.math.BigInteger.valueOf(mmToTwips(margin(document.getMarginBottom()))));
        margins.setLeft(java.math.BigInteger.valueOf(mmToTwips(margin(document.getMarginLeft()))));
    }

    private void appendStandaloneImage(XWPFDocument word, String url, String alt) {
        XWPFParagraph paragraph = word.createParagraph();
        if (!appendImage(paragraph.createRun(), url, alt)) word.removeBodyElement(word.getPosOfParagraph(paragraph));
    }

    private boolean appendImage(XWPFRun run, String url, String alt) {
        var path = fileService.resolveStoredFile(url);
        if (path.isEmpty()) return false;
        try (InputStream input = Files.newInputStream(path.get())) {
            BufferedImage image = ImageIO.read(path.get().toFile());
            int width = image == null ? 480 : Math.min(520, Math.max(80, image.getWidth()));
            int height = image == null ? 260 : Math.max(40, Math.round((float) image.getHeight() * width / image.getWidth()));
            run.addPicture(input, pictureType(path.get()), path.get().getFileName().toString(),
                    Units.toEMU(width), Units.toEMU(Math.min(height, 680)));
            return true;
        } catch (Exception ignored) {
            return false;
        }
    }

    private int pictureType(Path path) {
        String name = path.getFileName().toString().toLowerCase(Locale.ROOT);
        if (name.endsWith(".png")) return XWPFDocument.PICTURE_TYPE_PNG;
        if (name.endsWith(".gif")) return XWPFDocument.PICTURE_TYPE_GIF;
        if (name.endsWith(".bmp")) return XWPFDocument.PICTURE_TYPE_BMP;
        return XWPFDocument.PICTURE_TYPE_JPEG;
    }

    private void inlineStoredImages(Element body) {
        body.select("img[src]").forEach(image -> dataUri(image.attr("src"))
                .ifPresentOrElse(uri -> image.attr("src", uri), image::remove));
    }

    private java.util.Optional<String> dataUri(String url) {
        return fileService.resolveStoredFile(url).flatMap(path -> {
            try {
                String type = Files.probeContentType(path);
                if (type == null || !type.startsWith("image/")) return java.util.Optional.empty();
                return java.util.Optional.of("data:" + type + ";base64," + Base64.getEncoder().encodeToString(Files.readAllBytes(path)));
            } catch (IOException ignored) {
                return java.util.Optional.empty();
            }
        });
    }

    private Path resolveExportFont() {
        if (exportFontPath != null && !exportFontPath.isBlank()) {
            Path configured = Path.of(exportFontPath).toAbsolutePath().normalize();
            if (Files.isRegularFile(configured)) return configured;
        }
        for (String candidate : List.of("C:/Windows/Fonts/msyh.ttc", "/usr/share/fonts/opentype/noto/NotoSansCJK-Regular.ttc")) {
            Path path = Path.of(candidate);
            if (Files.isRegularFile(path)) return path;
        }
        return null;
    }

    private int margin(Integer value) { return value == null ? 20 : Math.max(5, Math.min(60, value)); }
    private long mmToTwips(int mm) { return Math.round(mm * 56.6929d); }
    private String cssText(String value) {
        if (value == null) return "";
        return value.replace("\\", "\\\\").replace("'", "\\'").replace("\n", " ").replace("\r", " ");
    }

    public record ExportedDocument(String fileName, byte[] content) {}

    private static class Style {
        boolean bold;
        boolean italic;
        boolean underline;
        boolean strike;
        boolean code;
        String fontFamily;
        Integer fontSize;
        String color;
        void apply(Element element) {
            String family = element.attr("data-font-family");
            fontFamily = switch (family) { case "sans" -> "Arial"; case "serif" -> "Times New Roman"; case "kai" -> "KaiTi"; case "mono" -> "Consolas"; default -> fontFamily; };
            String size = element.attr("data-font-size");
            if (size.matches("12|14|16|18|20|24|28|32")) fontSize = Math.max(8, Math.round(Integer.parseInt(size) * 3f / 4f));
            color = switch (element.attr("data-text-color")) { case "red" -> "DC2626"; case "blue" -> "2563EB"; case "green" -> "15803D"; case "gray" -> "64748B"; case "purple" -> "7E22CE"; default -> color; };
        }
        Style copy() { Style copy = new Style(); copy.bold=bold; copy.italic=italic; copy.underline=underline; copy.strike=strike; copy.code=code; copy.fontFamily=fontFamily; copy.fontSize=fontSize; copy.color=color; return copy; }
    }
}
