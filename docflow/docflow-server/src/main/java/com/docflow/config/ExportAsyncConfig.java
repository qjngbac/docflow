package com.docflow.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.web.servlet.config.annotation.AsyncSupportConfigurer;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.concurrent.ThreadPoolExecutor;

/** 为耗时的文档导出提供独立有界线程池，避免占满 Web 请求线程。 */
@Configuration
public class ExportAsyncConfig implements WebMvcConfigurer {

    @Value("${app.export.executor.core-size:2}")
    private int coreSize;

    @Value("${app.export.executor.max-size:4}")
    private int maxSize;

    @Value("${app.export.executor.queue-capacity:20}")
    private int queueCapacity;

    @Value("${app.export.timeout-millis:120000}")
    private long timeoutMillis;

    @Bean
    public ThreadPoolTaskExecutor documentExportExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setThreadNamePrefix("document-export-");
        executor.setCorePoolSize(coreSize);
        executor.setMaxPoolSize(maxSize);
        executor.setQueueCapacity(queueCapacity);
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.AbortPolicy());
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(30);
        return executor;
    }

    @Override
    public void configureAsyncSupport(AsyncSupportConfigurer configurer) {
        configurer.setTaskExecutor(documentExportExecutor());
        configurer.setDefaultTimeout(timeoutMillis);
    }
}
