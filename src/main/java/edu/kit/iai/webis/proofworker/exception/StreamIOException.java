/*
 * Copyright (c) 2025-2026
 * Karlsruhe Institute of Technology - Institute for Automation and Applied Informatics (IAI)
 */
package edu.kit.iai.webis.proofworker.exception;

@SuppressWarnings("unused")
public class StreamIOException extends RuntimeException {

    public StreamIOException(String message, Throwable cause) {
        super(message, cause);
    }

    public StreamIOException(String message) {
        super(message);
    }
}
