package com.docflow.security;

import com.docflow.common.BusinessException;
import com.docflow.common.ErrorCode;
import com.docflow.service.FileService;
import com.docflow.service.FileAccessService;
import org.springframework.beans.factory.annotation.Autowired;
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

/** 为私有上传文件生成短期签名地址，并校验访问时的路径、时效和签名。 */
@Service
public class SignedFileService {
    private static final Pattern MARKDOWN_FILE = Pattern.compile("(?<![A-Za-z0-9:/._?=&%\\-])(/files/[^\\s)\\]\\\"']+)");

    private final FileService fileService;
    private final FileAccessService fileAccess;
    private final byte[] secret;
    private final long urlLifetimeSeconds;

    public SignedFileService(FileService fileService,
                             String secret, long urlLifetimeSeconds) {
        this(fileService, null, secret, urlLifetimeSeconds);
    }

    @Autowired
    public SignedFileService(FileService fileService, FileAccessService fileAccess,
                             @Value("${file.signing-secret:docflow-local-file-signing-secret-change-in-production}") String secret,
                             @Value("${file.signed-url-seconds:300}") long urlLifetimeSeconds) {
        this.fileService = fileService;
        this.fileAccess = fileAccess;
        this.secret = secret.getBytes(StandardCharsets.UTF_8);
        this.urlLifetimeSeconds = Math.max(60L, Math.min(3600L, urlLifetimeSeconds));
    }

    public String sign(String fileUrl) {
        throw new BusinessException(ErrorCode.FORBIDDEN, "An authorized document share is required");
    }

    public String sign(String fileUrl, Long docId, String shareToken) {
        if (!StringUtils.hasText(fileUrl) || !fileUrl.startsWith("/files/")) return fileUrl;
        if (fileAccess == null) throw new BusinessException(ErrorCode.FORBIDDEN, "File access unavailable");
        fileAccess.requireShareFile(fileUrl, docId, shareToken);
        long expires = Instant.now().getEpochSecond() + urlLifetimeSeconds;
        return UriComponentsBuilder.fromPath("/api/v1/public-files")
                .queryParam("path", fileUrl)
                .queryParam("expires", expires)
                .queryParam("docId", docId)
                .queryParam("shareToken", shareToken)
                .queryParam("signature", signature(fileUrl, expires, docId, shareToken))
                .build().encode().toUriString();
    }

    public Optional<Path> resolve(String fileUrl, long expires, String suppliedSignature) {
        return Optional.empty();
    }

    public Optional<Path> resolve(String fileUrl, long expires, String suppliedSignature, Long docId, String shareToken) {
        long now = Instant.now().getEpochSecond();
        if (!FileAccessService.isCanonical(fileUrl) || expires < now || docId == null || !StringUtils.hasText(shareToken)
                || expires > now + 3600L || !StringUtils.hasText(suppliedSignature) || fileAccess == null) return Optional.empty();
        byte[] expected = signature(fileUrl, expires, docId, shareToken).getBytes(StandardCharsets.US_ASCII);
        byte[] supplied = suppliedSignature.getBytes(StandardCharsets.US_ASCII);
        if (!MessageDigest.isEqual(expected, supplied)) return Optional.empty();
        if (!fileAccess.canShareFile(fileUrl, docId, shareToken)) return Optional.empty();
        return fileService.resolveStoredFile(fileUrl);
    }

    public String rewriteContent(String content) {
        throw new BusinessException(ErrorCode.FORBIDDEN, "An authorized document share is required");
    }

    public String rewriteContent(String content, Long docId, String shareToken) {
        if (!StringUtils.hasText(content) || !content.contains("/files/")) return content;
        if (content.contains("<") && content.contains(">")) {
            org.jsoup.nodes.Document parsed = Jsoup.parseBodyFragment(content);
            parsed.outputSettings().prettyPrint(false);
            for (Element element : parsed.select("img[src^=/files/],a[href^=/files/]")) {
                String attribute = element.hasAttr("src") ? "src" : "href";
                element.attr(attribute, signIfAllowed(element.attr(attribute), docId, shareToken));
            }
            return parsed.body().html();
        }
        Matcher matcher = MARKDOWN_FILE.matcher(content);
        StringBuffer output = new StringBuffer();
        while (matcher.find()) matcher.appendReplacement(output, Matcher.quoteReplacement(signIfAllowed(matcher.group(1), docId, shareToken)));
        matcher.appendTail(output);
        return output.toString();
    }

    private String signIfAllowed(String url, Long docId, String token) {
        return fileAccess != null && fileAccess.canShareFile(url, docId, token) ? sign(url, docId, token) : url;
    }

    private String signature(String fileUrl, long expires, Long docId, String shareToken) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret, "HmacSHA256"));
            byte[] digest = mac.doFinal((fileUrl + "\n" + expires + "\n" + docId + "\n" + shareToken).getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(digest);
        } catch (Exception exception) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "无法生成文件访问地址");
        }
    }
}
