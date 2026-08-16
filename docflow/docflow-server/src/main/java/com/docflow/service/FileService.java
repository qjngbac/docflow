package com.docflow.service;

import com.docflow.common.BusinessException;
import com.docflow.common.ErrorCode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;
import com.docflow.security.MalwareScanner;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.Optional;
import java.io.InputStream;
import java.util.Arrays;

@Service
public class FileService {

    private static final Set<String> ALLOWED_EXTENSIONS = Set.of(
            "jpg", "jpeg", "png", "gif", "webp", "pdf", "doc", "docx", "xls", "xlsx", "txt", "md"
    );
    private static final Set<String> AVATAR_EXTENSIONS = Set.of("jpg", "jpeg", "png", "gif", "webp");
    private static final Set<String> EMBEDDED_IMAGE_EXTENSIONS = Set.of("jpg", "jpeg", "png", "gif", "webp", "bmp");
    private static final long MAX_AVATAR_SIZE = 5L * 1024 * 1024;
    private static final long MAX_FEEDBACK_IMAGE_SIZE = 5L * 1024 * 1024;

    @Value("${file.upload-dir:./uploads}")
    private String uploadDir;

    private final MalwareScanner malwareScanner;

    public FileService(MalwareScanner malwareScanner) {
        this.malwareScanner = malwareScanner;
    }

    public String upload(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "上传的文件不能为空");
        }

        String extension = validateAttachmentName(file.getOriginalFilename());
        inspect(file, extension);
        return store(file, extension, null);
    }

    public String validateAttachmentName(String filename) {
        String extension = extension(filename);
        if (!ALLOWED_EXTENSIONS.contains(extension)) throw new BusinessException(ErrorCode.BAD_REQUEST, "暂不支持这种文件格式");
        return extension;
    }

    public String storeCompletedUpload(Path source, String originalName) {
        String extension = validateAttachmentName(originalName);
        if (source == null || !Files.isRegularFile(source)) throw new BusinessException(ErrorCode.BAD_REQUEST, "没有找到已完成上传的文件");
        inspect(source, originalName, extension);
        String datePath = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy/MM/dd"));
        Path relativePath = Paths.get(datePath, UUID.randomUUID() + "." + extension);
        Path target = Paths.get(uploadDir).resolve(relativePath).normalize();
        try {
            Files.createDirectories(target.getParent());
            Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException exception) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "保存已上传文件失败");
        }
        return "/files/" + relativePath.toString().replace("\\", "/");
    }

    public String uploadAvatar(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "头像文件不能为空");
        }
        if (file.getSize() > MAX_AVATAR_SIZE) {
            throw new BusinessException(ErrorCode.PAYLOAD_TOO_LARGE, "头像文件不能超过 5 MB");
        }

        String extension = extension(file.getOriginalFilename());
        String contentType = file.getContentType();
        if (!AVATAR_EXTENSIONS.contains(extension)
                || contentType == null || !contentType.toLowerCase(Locale.ROOT).startsWith("image/")) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "头像只支持 JPG、PNG、GIF 或 WebP 图片");
        }
        inspect(file, extension);
        return store(file, extension, "avatars");
    }

    public String uploadFeedbackImage(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "反馈图片不能为空");
        }
        if (file.getSize() > MAX_FEEDBACK_IMAGE_SIZE) {
            throw new BusinessException(ErrorCode.PAYLOAD_TOO_LARGE, "每张反馈图片不能超过 5 MB");
        }
        String extension = extension(file.getOriginalFilename());
        String contentType = file.getContentType();
        if (!AVATAR_EXTENSIONS.contains(extension)
                || contentType == null || !contentType.toLowerCase(Locale.ROOT).startsWith("image/")) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "反馈图片仅支持 JPG、PNG、GIF 或 WebP");
        }
        inspect(file, extension);
        return store(file, extension, "feedback");
    }

    public void deleteByUrl(String fileUrl) {
        if (!StringUtils.hasText(fileUrl) || !fileUrl.startsWith("/files/")) {
            return;
        }
        Path root = Paths.get(uploadDir).toAbsolutePath().normalize();
        Path target = root.resolve(fileUrl.substring("/files/".length())).normalize();
        if (!target.startsWith(root)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "文件地址不正确");
        }
        try {
            Files.deleteIfExists(target);
        } catch (IOException e) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "删除文件失败");
        }
    }

    public Optional<Path> resolveStoredFile(String fileUrl) {
        if (!StringUtils.hasText(fileUrl) || !fileUrl.startsWith("/files/")) return Optional.empty();
        Path root = Paths.get(uploadDir).toAbsolutePath().normalize();
        Path target = root.resolve(fileUrl.substring("/files/".length())).normalize();
        if (!target.startsWith(root) || !Files.isRegularFile(target)) return Optional.empty();
        return Optional.of(target);
    }

    public String storeBytes(byte[] bytes, String extension, String prefix) {
        if (bytes == null || bytes.length == 0) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "文件内容不能为空");
        }
        String normalizedExtension = extension == null ? "" : extension.toLowerCase(Locale.ROOT);
        if (!EMBEDDED_IMAGE_EXTENSIONS.contains(normalizedExtension)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "暂不支持这种内嵌图片格式");
        }
        validateSignature(bytes, normalizedExtension);
        malwareScanner.scan(new java.io.ByteArrayInputStream(bytes), "embedded." + normalizedExtension);
        String datePath = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy/MM/dd"));
        String filename = UUID.randomUUID() + "." + normalizedExtension;
        Path relativePath = Paths.get(prefix, datePath, filename);
        Path target = Paths.get(uploadDir).resolve(relativePath).normalize();
        try {
            Files.createDirectories(target.getParent());
            Files.write(target, bytes);
        } catch (IOException e) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "保存文档内嵌图片失败");
        }
        return "/files/" + relativePath.toString().replace("\\", "/");
    }

    private String store(MultipartFile file, String extension, String prefix) {
        String datePath = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy/MM/dd"));
        String filename = UUID.randomUUID() + "." + extension;
        Path relativePath = StringUtils.hasText(prefix)
                ? Paths.get(prefix, datePath, filename)
                : Paths.get(datePath, filename);
        Path target = Paths.get(uploadDir).resolve(relativePath).normalize();

        try {
            Files.createDirectories(target.getParent());
            file.transferTo(target);
        } catch (IOException e) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "上传文件失败");
        }

        return "/files/" + relativePath.toString().replace("\\", "/");
    }

    public void inspect(MultipartFile file) {
        if (file == null || file.isEmpty()) throw new BusinessException(ErrorCode.BAD_REQUEST, "上传的文件不能为空");
        inspect(file, extension(file.getOriginalFilename()));
    }

    private void inspect(MultipartFile file, String extension) {
        try (InputStream input = file.getInputStream()) {
            byte[] header = input.readNBytes(16);
            validateSignature(header, extension);
        } catch (BusinessException exception) {
            throw exception;
        } catch (IOException exception) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "无法读取上传文件");
        }
        try (InputStream input = file.getInputStream()) {
            malwareScanner.scan(input, file.getOriginalFilename());
        } catch (BusinessException exception) {
            throw exception;
        } catch (IOException exception) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "无法读取上传文件");
        }
    }

    private void inspect(Path path, String filename, String extension) {
        try (InputStream input = Files.newInputStream(path)) {
            validateSignature(input.readNBytes(16), extension);
        } catch (BusinessException exception) {
            throw exception;
        } catch (IOException exception) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "无法读取上传文件");
        }
        malwareScanner.scan(path, filename);
    }

    private void validateSignature(byte[] header, String extension) {
        if (header.length >= 2 && header[0] == 'M' && header[1] == 'Z') {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "不允许上传可执行文件");
        }
        boolean valid = switch (extension) {
            case "jpg", "jpeg" -> startsWith(header, new int[]{0xff, 0xd8, 0xff});
            case "png" -> startsWith(header, new int[]{0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a});
            case "gif" -> startsWith(header, "GIF8".getBytes(java.nio.charset.StandardCharsets.US_ASCII));
            case "webp" -> startsWith(header, "RIFF".getBytes(java.nio.charset.StandardCharsets.US_ASCII))
                    && header.length >= 12 && new String(header, 8, 4, java.nio.charset.StandardCharsets.US_ASCII).equals("WEBP");
            case "bmp" -> startsWith(header, "BM".getBytes(java.nio.charset.StandardCharsets.US_ASCII));
            case "pdf" -> startsWith(header, "%PDF-".getBytes(java.nio.charset.StandardCharsets.US_ASCII));
            case "doc", "xls" -> startsWith(header, new int[]{0xd0, 0xcf, 0x11, 0xe0, 0xa1, 0xb1, 0x1a, 0xe1});
            case "docx", "xlsx" -> startsWith(header, "PK".getBytes(java.nio.charset.StandardCharsets.US_ASCII));
            case "txt", "md" -> !containsNullByte(header);
            default -> false;
        };
        if (!valid) throw new BusinessException(ErrorCode.BAD_REQUEST, "文件内容与扩展名不一致");
    }

    private boolean startsWith(byte[] value, byte[] expected) {
        return value.length >= expected.length && Arrays.equals(Arrays.copyOf(value, expected.length), expected);
    }

    private boolean startsWith(byte[] value, int[] expected) {
        if (value.length < expected.length) return false;
        for (int index = 0; index < expected.length; index++) {
            if ((value[index] & 0xff) != expected[index]) return false;
        }
        return true;
    }

    private boolean containsNullByte(byte[] value) {
        for (byte item : value) if (item == 0) return true;
        return false;
    }

    private String extension(String filename) {
        if (!StringUtils.hasText(filename) || !filename.contains(".")) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "文件名缺少扩展名");
        }
        return filename.substring(filename.lastIndexOf('.') + 1).toLowerCase(Locale.ROOT);
    }
}
