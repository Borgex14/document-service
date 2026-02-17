package com.itq.generator;

import org.springframework.web.client.RestTemplate;
import org.springframework.core.io.Resource;
import org.springframework.core.io.FileSystemResource;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicInteger;

public class DocumentGenerator {

    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("Usage: java -jar generator.jar <config.properties>");
            System.exit(1);
        }

        try {
            Properties config = new Properties();
            config.load(new FileSystemResource(args[0]).getInputStream());

            int count = Integer.parseInt(config.getProperty("document.count"));
            String apiUrl = config.getProperty("api.url");
            String author = config.getProperty("author");

            RestTemplate restTemplate = new RestTemplate();
            AtomicInteger progress = new AtomicInteger(0);

            System.out.println("Starting generation of " + count + " documents...");
            long startTime = System.currentTimeMillis();

            for (int i = 0; i < count; i++) {
                CreateDocumentRequest request = new CreateDocumentRequest();
                request.setAuthor(author);
                request.setTitle("Generated Document " + (i + 1));

                restTemplate.postForObject(
                        apiUrl + "/api/documents",
                        request,
                        DocumentDto.class
                );

                int current = progress.incrementAndGet();
                if (current % 10 == 0) {
                    System.out.printf("Progress: %d/%d documents created%n",
                            current, count);
                }
            }

            long duration = System.currentTimeMillis() - startTime;
            System.out.printf("Completed! Created %d documents in %d ms%n",
                    count, duration);

        } catch (Exception e) {
            System.err.println("Error: " + e.getMessage());
            System.exit(1);
        }
    }
}