/*
 * Copyright (c) 2025-2026
 * Karlsruhe Institute of Technology - Institute for Automation and Applied Informatics (IAI)
 */
package edu.kit.iai.webis.proofworker.services;

import edu.kit.iai.webis.proofutils.LoggingHelper;
import edu.kit.iai.webis.proofutils.exception.NotImplementedException;
import edu.kit.iai.webis.proofworker.util.ReaderHelper;

/**
 * reader for the interpretation of messages from the wrapper using a file
 */
public class OutputFileReader implements IReader {

    private final SyncController syncController;
    private final ReaderHelper readerHelper;
    private String path;

    public OutputFileReader(final SyncController actionController, final ReaderHelper readerHelper, String path) {
        this.syncController = actionController;
        this.readerHelper = readerHelper;
        this.path = path;
    }

    @Override
    @SuppressWarnings({"InfiniteLoopStatement", "BusyWait"})
    public void read() {
    	LoggingHelper.error().exception(new NotImplementedException("File-based communication is not yet implemented here!"))
    	.log("File-based communication is not yet implemented here!");
//        final var gson = new Gson();
//        this.syncController.getValueOutputWrappers().forEach((final SimulationPhase simulationPhase,
//                                                                final IValueOutputWrapper wrapper) -> {
//
//            if (wrapper.getInterfaceType().equals(InterfaceType.FILE) ) {
//
//                LoggingHelper.debug().log("\nreading from FILE   '" + this.path + "' ...");
//                Executors.newCachedThreadPool().submit(() -> {
//                    try (final var br =
//                                 new BufferedReader(
//                                         new InputStreamReader(
//                                                 new FileInputStream(this.path),
//                                                 StandardCharsets.UTF_8))) {
//                        String line;
//                        while (true) {
//                            line = br.readLine();
//                            if (line != null) {
//                                if (!line.equals("")) {
//                                    try {
//                                        final var object = gson.fromJson(line, JsonObject.class);
//                                        this.readerHelper.readStringObject(line);
//                                    } catch (JsonSyntaxException e) {
//                                        e.printStackTrace();
//                                    }
//                                }
//                            } else {
//                            	LoggingHelper.debug().log("\nSLEEPING 500ms!!! \n");
//                                Thread.sleep(500);
//                            }
//                        }
//                    } catch (Exception e) {
//                        final var message = StringTemplates.COULD_NOT_OPEN_FILE_READER;
//                        LoggingHelper.error().log(message);
//                        throw new StreamIOException(message, e);
//                    }
//                });
//            }
//        });
    }
}
