/*
 * Copyright (c) 2025-2026
 * Karlsruhe Institute of Technology - Institute for Automation and Applied Informatics (IAI)
 */
package edu.kit.iai.webis.proofworker.exception;

//@ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
public class RequestErrorException extends RuntimeException {

    public RequestErrorException(String message, Exception e) {
        super(message, e);
    }

    public RequestErrorException(String message) {
        super(message);
    }
}
