package com.itq.document.config;

import com.itq.document.repository.DocumentRepository;
import com.itq.document.service.DocumentService;
import com.itq.document.worker.ApproveWorker;
import com.itq.document.worker.SubmitWorker;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

@Configuration
@EnableScheduling
@ConfigurationProperties(prefix = "app.worker")
@Data
public class WorkerConfig {

    private int poolSize = 2;
    private SubmitConfig submit = new SubmitConfig();
    private ApproveConfig approve = new ApproveConfig();

    @Bean
    public ThreadPoolTaskScheduler taskScheduler() {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(poolSize);
        scheduler.setThreadNamePrefix("worker-");
        return scheduler;
    }

    @Bean
    public SubmitWorker submitWorker(
            DocumentRepository documentRepository,
            DocumentService documentService) {
        return new SubmitWorker(
                documentRepository,
                documentService,
                submit.getFixedDelay(),
                submit.getBatchSize()
        );
    }

    @Bean
    public ApproveWorker approveWorker(
            DocumentRepository documentRepository,
            DocumentService documentService) {
        return new ApproveWorker(
                documentRepository,
                documentService,
                approve.getFixedDelay(),
                approve.getBatchSize()
        );
    }

    @Data
    public static class SubmitConfig {
        private long fixedDelay = 60000;
        private int batchSize = 100;
    }

    @Data
    public static class ApproveConfig {
        private long fixedDelay = 60000;
        private int batchSize = 100;
    }
}