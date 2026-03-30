/*
 * Copyright (c) 2025-2026
 * Karlsruhe Institute of Technology - Institute for Automation and Applied Informatics (IAI)
 */
package edu.kit.iai.webis.proofworker.services;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

import edu.kit.iai.webis.proofutils.LoggingHelper;
import edu.kit.iai.webis.proofworker.exception.StreamIOException;
import edu.kit.iai.webis.proofworker.util.ReaderHelper;
import edu.kit.iai.webis.proofworker.util.StringTemplates;

/**
 * the OutputSocketReader is a socket SERVER implementation that reads data (messages) from the wrapper (Python).
 * The server part is started in the constructor and is used later in method {@link #read()}.
 * A client part is used for control responses as 'ok' and/or 'close' etc.
 * As IP, 'localhost' (127.0.0.1) is used, because worker and wrapper are using the same container.
 */
public class OutputSocketReader implements IReader{

	private ServerSocket serverSocket;
    private Socket clientSocket;
    private final ReaderHelper readerHelper;
    private int port;

	public OutputSocketReader(int port, ReaderHelper readerHelper) {
		super();
		this.readerHelper = readerHelper;
		this.port = port;
        try {
			LoggingHelper.debug().log("\nstarting Reader (Server) with port "+ this.port + " ...");
			this.serverSocket = new ServerSocket(this.port);
			LoggingHelper.debug().log("server started ... \n");
		} catch (IOException e) {
			e.printStackTrace();
		}
	}


	/**
	 * {@inheritDoc}
	 */
	@Override
	public void read() {

		new Thread(() -> {
			try {
				this.clientSocket = this.serverSocket.accept();
			} catch (IOException e) {
				e.printStackTrace();
			}

			String fromClient;
			try (final var reader = new BufferedReader(new InputStreamReader(this.clientSocket.getInputStream(), StandardCharsets.UTF_8))) {
				while ( (fromClient = reader.readLine()) != null) {
					if (!fromClient.isEmpty()) {
						try {
							LoggingHelper.debug().log("Reading line from wrapper: -------> " + fromClient);
							// pre-check for a JSON object to avoid unnecessary exceptions:
							if( fromClient.startsWith("{") ){
//                            if (this.isJSONValid(line)) {
								this.readerHelper.readStringObject(fromClient);
							}
							else if( fromClient.startsWith("close") ) {
//                                this.out.println("close");
								this.stop();
								LoggingHelper.debug().log("socket closed");
							}
						} catch (final Exception e) {
							e.printStackTrace();
						}
//                        LoggingHelper.debug().log("Sending response OK back to wrapper ... ");
//                        this.out.println("ok");
//                        LoggingHelper.debug().log("Sending response OK :  DONE");
					}
					else {
						LoggingHelper.error().log("fromClient is empty!    continuing ...\n");
					}
					LoggingHelper.trace().log("WHILE-LOOP: Reader is ready:  " + reader.ready() + "\n");
				}

			} catch (final IOException e) {
				//REFACTOR: use ERROR_ACCESSING_STREAM_READER
				final var message = StringTemplates.COULD_NOT_OPEN_STREAM_READER;
				LoggingHelper.error().log(message + ", reason: " + e.getMessage() );
				throw new StreamIOException(message, e);
			}
		}).start();

    }

    public void stop() {
        try{
//          in.close();
//          this.out.close();
          this.clientSocket.close();
          this.serverSocket.close();
        }catch( Exception e ){
            e.printStackTrace();
        }
      }
}
