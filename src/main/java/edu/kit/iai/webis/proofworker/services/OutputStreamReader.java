/*
 * Copyright (c) 2025-2026
 * Karlsruhe Institute of Technology - Institute for Automation and Applied Informatics (IAI)
 */
package edu.kit.iai.webis.proofworker.services;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

import edu.kit.iai.webis.proofutils.LoggingHelper;
import edu.kit.iai.webis.proofworker.exception.StreamIOException;
import edu.kit.iai.webis.proofworker.util.ReaderHelper;
import edu.kit.iai.webis.proofworker.util.StringTemplates;

/**
 * reader for the interpretation of messages from the wrapper using stdio
 */
public class OutputStreamReader implements IReader {

	private BufferedInputStream reader;
    private final ReaderHelper readerHelper;


    public OutputStreamReader(final BufferedInputStream processOutput, final ReaderHelper readerHelper) {
        this.reader = processOutput;
        this.readerHelper = readerHelper;
    }

    @Override
    public void read() {
//        Executors.newSingleThreadExecutor().submit(() -> {
    	new Thread(() -> {
            String line;
            try (final var reader = new BufferedReader(new InputStreamReader(this.reader, StandardCharsets.UTF_8))) {
                while ((line = reader.readLine()) != null) {
                    if (!line.isEmpty()) {
                        try {
                            LoggingHelper.debug().log("Reading line from wrapper: -------> " + line);
                            // pre-check for a JSON object to avoid unnecessary exceptions:
                            if( line.startsWith("{") ){
                               this.readerHelper.readStringObject(line);
                            }
                        } catch (final Exception e) {
                            e.printStackTrace();
                        }
                    }
                }
                LoggingHelper.debug().log("\nReader ended unexpectedly !  line=%s\n", line);
            } catch (final IOException e) {
                // REFACTOR: use ERROR_ACCESSING_STREAM_READER
                final var message = StringTemplates.COULD_NOT_OPEN_STREAM_READER;
                LoggingHelper.error().log(message + ", reason: " + e.getMessage() );
                throw new StreamIOException(message, e);
            }
        }).start();
    }

}
