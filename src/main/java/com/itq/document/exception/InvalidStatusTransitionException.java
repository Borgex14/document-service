package com.itq.document.exception;

public class InvalidStatusTransitionException extends BaseException {

    public InvalidStatusTransitionException(Long documentId,
                                            String currentStatus,
                                            String targetStatus) {
        super("INVALID_STATUS_TRANSITION",
                String.format("Cannot transition document %d from %s to %s",
                        documentId, currentStatus, targetStatus));
    }
}