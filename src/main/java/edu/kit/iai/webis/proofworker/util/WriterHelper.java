/*
 * Copyright (c) 2025-2026
 * Karlsruhe Institute of Technology - Institute for Automation and Applied Informatics (IAI)
 */
package edu.kit.iai.webis.proofworker.util;

import java.io.FileWriter;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

import org.springframework.stereotype.Component;

import com.google.gson.Gson;

import edu.kit.iai.webis.proofutils.LoggingHelper;
import edu.kit.iai.webis.proofutils.message.IMessage;
import edu.kit.iai.webis.proofutils.message.SyncMessage;
import edu.kit.iai.webis.proofutils.message.ValueMessage;
import edu.kit.iai.webis.proofutils.model.InterfaceType;
import edu.kit.iai.webis.proofutils.wrapper.Execution;
import edu.kit.iai.webis.proofworker.exception.StreamIOException;

/**
 * a helper class for the writing of messages (a  {@link ValueMessage} or a {@link SyncMessage})
 * to the wrapper using a file or a stream
 */
@Component
public class WriterHelper {

    private final Execution execution;
    private final Gson gson;

    public WriterHelper(final Execution execution,
                        final Gson gson) {
        this.execution = execution;
        this.gson = gson;
    }


    /**
     * write a ValueMessage to the wrapper via stream, file, or socket
     * @param message
     * @param interfaceType
     * @param path
     * @param dataOutputStream
     */
	public void writeMessage(
            final IMessage message,
            final InterfaceType interfaceType,
            final String path,
            final OutputStream dataOutputStream)
	{
        if (interfaceType == InterfaceType.STDIO   ||  interfaceType == InterfaceType.SOCKET) {
            this.writeToStream(dataOutputStream, message);
        } else if (interfaceType == InterfaceType.FILE) {
            this.writeToFile(path, message);
        }
    }

    public synchronized void writeToFile(final String path, final IMessage message) {
        try {
            final var fileWriter = new FileWriter(path, true);
            LoggingHelper.debug().log("=========> sending == '%s' Message to wrapper (file:%s), Message: %s", message.getType(), path, this.gson.toJson(message));

            fileWriter.write("%s\n".formatted(this.gson.toJson(message)));
            fileWriter.close();
        } catch (final IOException e) {
            final var error = "Cant write to filestream";
            LoggingHelper.error().log(error);
            throw new StreamIOException(error, e);
        }
    }

    public synchronized void writeToStream(final OutputStream dataOutputStream, final IMessage message) {
        try {
        	String strMsg = this.gson.toJson(message)+"\n";
            byte[] bytesMsg = strMsg.getBytes(StandardCharsets.UTF_8);
            LoggingHelper.trace().log("=========> sending  '%s' Message to Wrapper (outputStream:%s), Message: >>%s<<", message.getType(), dataOutputStream, strMsg);

            if( bytesMsg.length > 1024 && (this.execution.getInterfaceType() == InterfaceType.SOCKET) ) {  // eventually reset for small messages (99 %) ??
            	String size = "{\"MSGSIZE\":\"" + (bytesMsg.length+1) + "\"}\n";
            	dataOutputStream.write(size.getBytes(StandardCharsets.UTF_8));
                dataOutputStream.flush();
                LoggingHelper.trace().log("=========> new Size sent == ");
            }
            dataOutputStream.write(bytesMsg);
            dataOutputStream.flush();
            LoggingHelper.debug().log("=========> Message sent == ");
        } catch (final Exception e) {
            final String error = "Cant write to stream '" + dataOutputStream + "'! ";
            LoggingHelper.error().log(error + ", reason: " + e.getMessage());
            throw new StreamIOException(error, e);
        }
    }


}
