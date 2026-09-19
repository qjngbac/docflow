package com.docflow;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

@SpringBootApplication
@MapperScan("com.docflow.mapper")
@EnableScheduling
public class DocflowApplication {

    private static final String UNIX_DOMAIN_TMPDIR = "jdk.net.unixdomain.tmpdir";

    public static void main(String[] args) {
        configureWindowsNioTempDirectory();
        SpringApplication.run(DocflowApplication.class, args);
        System.out.println("====================================");
        System.out.println("  DocFlow started successfully");
        System.out.println("  API: http://localhost:8080");
        System.out.println("====================================");
    }

    /** 避开部分Windows用户临时目录无法创建JDK NIO内部套接字的问题。 */
    private static void configureWindowsNioTempDirectory() {
        if (!System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win")
                || System.getProperty(UNIX_DOMAIN_TMPDIR) != null) {
            return;
        }
        String publicDirectory = System.getenv("PUBLIC");
        if (publicDirectory == null || publicDirectory.isBlank()) {
            return;
        }
        Path nioTempDirectory = Path.of(publicDirectory, "DocFlowTmp");
        try {
            Files.createDirectories(nioTempDirectory);
            System.setProperty(UNIX_DOMAIN_TMPDIR, nioTempDirectory.toString());
        } catch (IOException ignored) {
            // 保留JDK默认行为，启动失败时仍会输出原始网络异常。
        }
    }
}
