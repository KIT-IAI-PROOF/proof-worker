/*
 * Copyright (c) 2025-2026
 * Karlsruhe Institute of Technology - Institute for Automation and Applied Informatics (IAI)
 */
package edu.kit.iai.webis.proofworker.exception;

@SuppressWarnings("unused")
public class IOInterfaceSetupException extends RuntimeException {

    public IOInterfaceSetupException(String message, Throwable cause) {
        super(message, cause);
    }

    public IOInterfaceSetupException(String message) {
        super(message);
    }
}
