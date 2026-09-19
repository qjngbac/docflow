package com.docflow.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/** 识别当前文档正文是 Markdown 还是富文本，并生成列表页使用的纯文本摘要。 */
final class DocumentContentUtils {

    private DocumentContentUtils() {
    }

    static String summary(String content) {
        if (content == null) {
            return "";
        }
        String plain = content.replaceAll("<[^>]+>", " ")
                .replaceAll("#|\\*|`|>|\\[|\\]|\\(|\\)", "")
                .replaceAll("\\s+", " ").trim();
        return plain.length() > 200 ? plain.substring(0, 200) : plain;
    }

    static String sha256(String content) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest((content == null ? "" : content).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is not available", e);
        }
    }
}
