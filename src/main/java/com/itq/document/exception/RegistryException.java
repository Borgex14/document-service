package com.itq.document.exception;

public class RegistryException extends BaseException {

    public RegistryException(String message) {
        super("REGISTRY_ERROR", message);
    }

    public RegistryException(Long documentId, Throwable cause) {
        super("REGISTRY_ERROR",
                String.format("Failed to register document %d in registry", documentId));
    }
}