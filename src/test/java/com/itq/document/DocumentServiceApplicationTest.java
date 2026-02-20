package com.itq.document;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class DocumentServiceApplicationTest {

    @Autowired
    private ApplicationContext applicationContext;

    @Test
    void contextLoads() {
        // Проверяем, что контекст Spring загружается
        assertThat(applicationContext).isNotNull();
    }

    @Test
    void mainMethodRuns() {
        // Просто проверяем, что main метод не падает
        DocumentServiceApplication.main(new String[]{});
    }
}