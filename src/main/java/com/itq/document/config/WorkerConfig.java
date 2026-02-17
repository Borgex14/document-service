package com.itq.document.config;

import com.itq.document.worker.ApproveWorker;
import com.itq.document.worker.SubmitWorker;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

@Configuration
@EnableScheduling
@ConfigurationProperties(prefix = "app.worker")
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
    public SubmitWorker submitWorker() {
        return new SubmitWorker(submit.getFixedDelay(), submit.getBatchSize());
    }

    @Bean
    public ApproveWorker approveWorker() {
        return new ApproveWorker(approve.getFixedDelay(), approve.getBatchSize());
    }

    // Getters and setters
    public static class SubmitConfig {
        private long fixedDelay = 60000; // 1 minute
        private int batchSize = 100;
        // getters and setters
    }

    public static class ApproveConfig {
        private long fixedDelay = 60000; // 1 minute
        private int batchSize = 100;
        // getters and setters
    }
}