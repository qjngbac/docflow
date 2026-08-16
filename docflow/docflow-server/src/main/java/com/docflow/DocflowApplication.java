package com.docflow;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@MapperScan("com.docflow.mapper")
@EnableScheduling
public class DocflowApplication {

    public static void main(String[] args) {
        SpringApplication.run(DocflowApplication.class, args);
        System.out.println("====================================");
        System.out.println("  DocFlow started successfully");
        System.out.println("  API: http://localhost:8080");
        System.out.println("====================================");
    }
}
