/*
 * Copyright (c) 2025-2026
 * Karlsruhe Institute of Technology - Institute for Automation and Applied Informatics
 */

package edu.kit.iai.webis.proofworker.exception;

public class ValueConfigException extends RuntimeException {

    public ValueConfigException(String message, Throwable cause) {
        super(message, cause);
    }

    public ValueConfigException(String message) {
        super(message);
    }

    public ValueConfigException(Throwable cause) {
        super(cause);
    }
}
