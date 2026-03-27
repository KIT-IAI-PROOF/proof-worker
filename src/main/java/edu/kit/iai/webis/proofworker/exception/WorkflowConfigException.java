/*
 * Copyright (c) 2025-2026
 * Karlsruhe Institute of Technology - Institute for Automation and Applied Informatics (IAI)
 */
package edu.kit.iai.webis.proofworker.exception;

@SuppressWarnings("unused")
public class WorkflowConfigException extends RuntimeException {

    public WorkflowConfigException(String message, Throwable cause) {
        super(message, cause);
    }

    public WorkflowConfigException(Throwable cause) {
        super(cause);
    }

    public WorkflowConfigException(String message) {
        super(message);
    }
}
