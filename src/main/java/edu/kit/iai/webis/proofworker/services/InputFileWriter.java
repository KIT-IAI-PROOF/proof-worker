/*
 * Copyright (c) 2025-2026
 * Karlsruhe Institute of Technology - Institute for Automation and Applied Informatics (IAI)
 */
package edu.kit.iai.webis.proofworker.services;

import edu.kit.iai.webis.proofutils.model.InterfaceType;
import edu.kit.iai.webis.proofutils.message.SyncMessage;
import edu.kit.iai.webis.proofutils.message.ValueMessage;
import edu.kit.iai.webis.proofworker.util.WriterHelper;

/**
 * Writer for writing messages for a wrapper to a file
 */
public class InputFileWriter implements IWriter {

    private final String path;
    private final WriterHelper writerHelper;

    public InputFileWriter(final String path, final WriterHelper writerHelper) {
        this.path = path;
        this.writerHelper = writerHelper;
    }

    /**
     * Write a value message to a file
     *
     * @param valueMessage the {@link ValueMessage} containing values and phase to write
     */
    @Override
    public void writeValueMessage( ValueMessage valueMessage ) {
    	this.writerHelper.writeMessage(valueMessage, InterfaceType.FILE, this.path, null );
    }

    /**
     * Write a sync message to a file
     *
     * @param syncMessage the {@link SyncMessage}
     */
    @Override
    public void writeSyncMessage( SyncMessage syncMessage ) {
    	this.writerHelper.writeMessage(syncMessage, InterfaceType.FILE, this.path, null );
    }

}
