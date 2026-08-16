package com.docflow.security;

import com.docflow.common.BusinessException;
import com.docflow.common.ErrorCode;
import com.docflow.service.FileService;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Element;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.util.UriComponentsBuilder;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Base64;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class SignedFileService {
    private static final Pattern MARKDOWN_FILE = Pattern.compile("(?<![A-Za-z0-9])(/files/[^\\s)\\]\\\"']+)");

    private final FileService fileService;
    private final byte[] secret;
    private final long urlLifetimeSeconds;

    public SignedFileService(FileService fileService,
                             @Value("${file.signing-secret:docflow-local-file-signing-secret-change-in-production}") String secret,
                             @Value("${file.signed-url-seconds:300}") long urlLifetimeSeconds) {
        this.fileService = fileService;
        this.secret = secret.getBytes(StandardCharsets.UTF_8);
        this.urlLifetimeSeconds = Math.max(60L, Math.min(3600L, urlLifetimeSeconds));
    }

    public String sign(String fileUrl) {
        if (!StringUtils.hasText(fileUrl) || !fileUrl.startsWith("/files/")) return fileUrl;
        long expires = Instant.now().getEpochSecond() + urlLifetimeSeconds;
        return UriComponentsBuilder.fromPath("/api/v1/public-files")
                .queryParam("path", fileUrl)
                .queryParam("expires", expires)
                .queryParam("signature", signature(fileUrl, expires))
                .build().encode().toUriString();
    }

    public Optional<Path> resolve(String fileUrl, long expires, String suppliedSignature) {
        long now = Instant.now().getEpochSecond();
        if (!StringUtils.hasText(fileUrl) || !fileUrl.startsWith("/files/") || expires < now
                || expires > now + 3600L || !StringUtils.hasText(suppliedSignature)) return Optional.empty();
        byte[] expected = signature(fileUrl, expires).getBytes(StandardCharsets.US_ASCII);
        byte[] supplied = suppliedSignature.getBytes(StandardCharsets.US_ASCII);
        if (!MessageDigest.isEqual(expected, supplied)) return Optional.empty();
        return fileService.resolveStoredFile(fileUrl);
    }

    public String rewriteContent(String content) {
        if (!StringUtils.hasText(content) || !content.contains("/files/")) return content;
        if (content.contains("<") && content.contains(">")) {
            org.jsoup.nodes.Document parsed = Jsoup.parseBodyFragment(content);
            parsed.outputSettings().prettyPrint(false);
            for (Element element : parsed.select("img[src^=/files/],a[href^=/files/]")) {
                String attribute = element.hasAttr("src") ? "src" : "href";
                element.attr(attribute, sign(element.attr(attribute)));
            }
            return parsed.body().html();
        }
        Matcher matcher = MARKDOWN_FILE.matcher(content);
        StringBuffer output = new StringBuffer();
        while (matcher.find()) matcher.appendReplacement(output, Matcher.quoteReplacement(sign(matcher.group(1))));
        matcher.appendTail(output);
        return output.toString();
    }

    private String signature(String fileUrl, long expires) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret, "HmacSHA256"));
            byte[] digest = mac.doFinal((fileUrl + "\n" + expires).getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(digest);
        } catch (Exception exception) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "无法生成文件访问地址");
        }
    }
}
