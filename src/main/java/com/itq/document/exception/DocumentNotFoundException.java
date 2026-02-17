package com.itq.document.exception;

public class DocumentNotFoundException extends BaseException {

    public DocumentNotFoundException(Long documentId) {
        super("DOCUMENT_NOT_FOUND",
                String.format("Document with id %d not found", documentId));
    }

    public DocumentNotFoundException(String documentNumber) {
        super("DOCUMENT_NOT_FOUND",
                String.format("Document with number %s not found", documentNumber));
    }
}