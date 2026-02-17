package com.itq.document.exception;

public class ValidationException extends BaseException {

    public ValidationException(String message) {
        super("VALIDATION_ERROR", message);
    }

    public ValidationException(String field, String message) {
        super("VALIDATION_ERROR",
                String.format("Field '%s': %s", field, message));
    }
}