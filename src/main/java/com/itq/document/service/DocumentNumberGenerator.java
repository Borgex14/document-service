package com.itq.document.service;

import org.springframework.stereotype.Component;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.atomic.AtomicLong;

@Component
public class DocumentNumberGenerator {

    private final AtomicLong counter = new AtomicLong(1);
    private final DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyyMMdd");

    public String generate() {
        String date = LocalDateTime.now().format(formatter);
        long sequence = counter.getAndIncrement();
        return String.format("DOC-%s-%06d", date, sequence);
    }
}