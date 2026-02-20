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

/**
 * Конфигурация для фоновых воркеров, обрабатывающих документы.
 * <p>
 * Данный класс настраивает и создает компоненты для автоматической обработки документов:
 * <ul>
 *   <li>{@link SubmitWorker} - воркер для отправки черновиков на утверждение</li>
 *   <li>{@link ApproveWorker} - воркер для утверждения документов</li>
 *   <li>{@link ThreadPoolTaskScheduler} - планировщик задач для выполнения воркеров</li>
 * </ul>
 * </p>
 *
 * <h2>Конфигурация через application.yml/properties:</h2>
 * <pre>
 * app:
 *   worker:
 *     pool-size: 4
 *     submit:
 *       fixed-delay: 30000
 *       batch-size: 50
 *     approve:
 *       fixed-delay: 30000
 *       batch-size: 50
 * </pre>
 *
 * <h2>Значения по умолчанию:</h2>
 * <ul>
 *   <li>pool-size: 2 потока</li>
 *   <li>submit.fixed-delay: 60000 мс (1 минута)</li>
 *   <li>submit.batch-size: 100 документов</li>
 *   <li>approve.fixed-delay: 60000 мс (1 минута)</li>
 *   <li>approve.batch-size: 100 документов</li>
 * </ul>
 *
 * @author Borgex Team
 * @version 1.0
 * @since 2026-02-20
 * @see SubmitWorker
 * @see ApproveWorker
 * @see ThreadPoolTaskScheduler
 */
@Configuration
@EnableScheduling
@ConfigurationProperties(prefix = "app.worker")
@Data
public class WorkerConfig {

    /**
     * Размер пула потоков для планировщика задач.
     * Определяет максимальное количество одновременно работающих воркеров.
     */
    private int poolSize = 2;

    /**
     * Конфигурация для воркера отправки документов.
     */
    private SubmitConfig submit = new SubmitConfig();

    /**
     * Конфигурация для воркера утверждения документов.
     */
    private ApproveConfig approve = new ApproveConfig();

    /**
     * Создает и настраивает планировщик задач для выполнения воркеров.
     * <p>
     * Планировщик использует пул потоков размером {@link #poolSize} и
     * префикс имени потоков "worker-" для удобной идентификации в логах.
     * </p>
     *
     * @return настроенный экземпляр {@link ThreadPoolTaskScheduler}
     */
    @Bean
    public ThreadPoolTaskScheduler taskScheduler() {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(poolSize);
        scheduler.setThreadNamePrefix("worker-");
        return scheduler;
    }

    /**
     * Создает и настраивает воркер для отправки черновиков на утверждение.
     * <p>
     * Воркер будет автоматически запускаться с периодичностью,
     * заданной в {@link SubmitConfig#getFixedDelay()} и обрабатывать
     * пачки документов размером {@link SubmitConfig#getBatchSize()}.
     * </p>
     *
     * @param documentRepository репозиторий для доступа к документам
     * @param documentService сервис для выполнения операций с документами
     * @return настроенный экземпляр {@link SubmitWorker}
     * @see SubmitWorker
     */
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

    /**
     * Создает и настраивает воркер для утверждения документов.
     * <p>
     * Воркер будет автоматически запускаться с периодичностью,
     * заданной в {@link ApproveConfig#getFixedDelay()} и обрабатывать
     * пачки документов размером {@link ApproveConfig#getBatchSize()}.
     * </p>
     *
     * @param documentRepository репозиторий для доступа к документам
     * @param documentService сервис для выполнения операций с документами
     * @return настроенный экземпляр {@link ApproveWorker}
     * @see ApproveWorker
     */
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

    /**
     * Конфигурация для воркера отправки документов.
     * <p>
     * Содержит настройки периодичности и размера пачки для {@link SubmitWorker}.
     * </p>
     *
     * @see SubmitWorker
     */
    @Data
    public static class SubmitConfig {
        /**
         * Задержка между запусками воркера в миллисекундах.
         */
        private long fixedDelay = 60000;

        /**
         * Количество документов, обрабатываемых за один цикл.
         */
        private int batchSize = 100;
    }

    /**
     * Конфигурация для воркера утверждения документов.
     * <p>
     * Содержит настройки периодичности и размера пачки для {@link ApproveWorker}.
     * </p>
     *
     * @see ApproveWorker
     */
    @Data
    public static class ApproveConfig {
        /**
         * Задержка между запусками воркера в миллисекундах.
         */
        private long fixedDelay = 60000;

        /**
         * Количество документов, обрабатываемых за один цикл.
         */
        private int batchSize = 100;
    }
}