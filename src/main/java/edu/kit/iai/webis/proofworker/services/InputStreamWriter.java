/*
 * Copyright (c) 2025-2026
 * Karlsruhe Institute of Technology - Institute for Automation and Applied Informatics (IAI)
 */
package edu.kit.iai.webis.proofworker.services;

import java.io.OutputStream;
import java.io.PipedOutputStream;

import edu.kit.iai.webis.proofutils.model.InterfaceType;
import edu.kit.iai.webis.proofutils.message.SyncMessage;
import edu.kit.iai.webis.proofutils.message.ValueMessage;
import edu.kit.iai.webis.proofutils.LoggingHelper;
import edu.kit.iai.webis.proofworker.util.WriterHelper;

/**
 * Writer for writing messages for a wrapper to a stream (stdio)
 */
public class InputStreamWriter implements IWriter {

    private final OutputStream dataOutputStream;
    private final WriterHelper writerHelper;

    public InputStreamWriter(final OutputStream dataOutputStream, final WriterHelper writerHelper) {
    	this.dataOutputStream = dataOutputStream;
    	if( !(this.dataOutputStream instanceof PipedOutputStream) ) {
    		LoggingHelper.warn().log("creating InputStreamWriter::  The outputStream should be a PipedInputStream!" );
    	}
        this.writerHelper = writerHelper;
    }

    /**
     * Write a value message to the stream
     *
     * @param valueMessage the {@link ValueMessage} containing the values
     */
    @Override
    public void writeValueMessage( final ValueMessage valueMessage ) {
    	this.writerHelper.writeMessage(valueMessage, InterfaceType.STDIO, null, this.dataOutputStream );
    }

    /**
     * Write sync message to the stream
     *
     * @param syncMessage the {@link SyncMessage}
     */
    @Override
	public void writeSyncMessage(final SyncMessage syncMessage) {
    	this.writerHelper.writeToStream( this.dataOutputStream, syncMessage );
    }

}
