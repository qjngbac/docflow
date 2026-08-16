package com.docflow.service;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.safety.Safelist;
import org.springframework.stereotype.Component;

@Component
public class HtmlSanitizer {

    private static final String LOCAL_FILE_ORIGIN = "https://docflow.local";

    private final Safelist safelist = Safelist.relaxed()
            .addTags("h1", "h2", "h3", "h4", "h5", "h6", "s", "strike", "del",
                    "u", "code", "pre", "blockquote", "hr", "thead", "tbody", "tfoot",
                    "input")
            .addAttributes("a", "target", "rel")
            .addAttributes("img", "width", "height", "loading", "decoding")
            .addAttributes("th", "colspan", "rowspan", "data-colwidth")
            .addAttributes("td", "colspan", "rowspan", "data-colwidth")
            .addAttributes("input", "type", "checked", "disabled")
            .addAttributes(":all", "class", "title", "data-type", "data-latex", "data-mathml", "data-comment-id",
                    "data-change-id", "data-change-type", "data-author-id", "data-author-name",
                    "data-last-edit-by", "data-last-edit-name", "data-docflow-style", "data-font-family",
                    "data-font-size", "data-text-color", "data-text-align", "data-line-height", "data-indent",
                    "data-collapsed", "data-equation-label", "data-equation-number", "data-formula-kind",
                    "data-footnote-id", "data-footnote-text", "data-citation-key", "data-citation-title",
                    "data-citation-url")
            .addProtocols("a", "href", "http", "https", "mailto")
            .addProtocols("img", "src", "http", "https")
            .preserveRelativeLinks(true);

    public String clean(String html) {
        if (html == null || html.isBlank()) {
            return "";
        }
        Document.OutputSettings outputSettings = new Document.OutputSettings().prettyPrint(false);
        String prepared = html.replace("src=\"/files/", "src=\"" + LOCAL_FILE_ORIGIN + "/files/")
                .replace("src='/files/", "src='" + LOCAL_FILE_ORIGIN + "/files/");
        return Jsoup.clean(prepared, "", safelist, outputSettings)
                .replace(LOCAL_FILE_ORIGIN + "/files/", "/files/");
    }
}
