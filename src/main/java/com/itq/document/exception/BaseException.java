package com.itq.document.exception;

import lombok.Getter;

@Getter
public abstract class BaseException extends RuntimeException {
    private final String code;
    private final String message;

    protected BaseException(String code, String message) {
        super(message);
        this.code = code;
        this.message = message;
    }
}