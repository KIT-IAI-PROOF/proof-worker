/*
 * Copyright (c) 2025-2026
 * Karlsruhe Institute of Technology - Institute for Automation and Applied Informatics (IAI)
 */
package edu.kit.iai.webis.proofworker.exception;

public class BlockConfigException extends RuntimeException {

    public BlockConfigException(String message, Throwable cause) {
        super(message, cause);
    }

    public BlockConfigException(String message) {
        super(message);
    }

    public BlockConfigException(Throwable cause) {
        super(cause);
    }
}
