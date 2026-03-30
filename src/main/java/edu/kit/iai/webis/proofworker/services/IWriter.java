/*
 * Copyright (c) 2025-2026
 * Karlsruhe Institute of Technology - Institute for Automation and Applied Informatics (IAI)
 */
package edu.kit.iai.webis.proofworker.services;

import edu.kit.iai.webis.proofutils.message.SyncMessage;
import edu.kit.iai.webis.proofutils.message.ValueMessage;

/**
 * common Interface for writing messages
 */
public interface IWriter {

	/**
	 * write a {@link ValueMessage}
	 * @param valueMessage the {@link ValueMessage}
	 */
    void writeValueMessage( final ValueMessage valueMessage );

    /**
     * write a {@link SyncMessage}
     * @param syncMessage the {@link SyncMessage}
     */
    void writeSyncMessage( final SyncMessage syncMessage );
}
