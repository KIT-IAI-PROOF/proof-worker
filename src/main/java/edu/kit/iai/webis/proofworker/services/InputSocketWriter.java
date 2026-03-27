/*
 * Copyright (c) 2025-2026
 * Karlsruhe Institute of Technology - Institute for Automation and Applied Informatics (IAI)
 */
package edu.kit.iai.webis.proofworker.services;

import java.io.IOException;
import java.io.OutputStream;
import java.net.Socket;
import java.net.UnknownHostException;

import edu.kit.iai.webis.proofutils.model.InterfaceType;
import edu.kit.iai.webis.proofutils.message.SyncMessage;
import edu.kit.iai.webis.proofutils.message.ValueMessage;
import edu.kit.iai.webis.proofutils.LoggingHelper;
import edu.kit.iai.webis.proofworker.util.WriterHelper;

public class InputSocketWriter implements IWriter{

    private int port;
    private Socket clientSocket;
    private final WriterHelper writerHelper;
    private OutputStream dataOutputStream;

	public InputSocketWriter(int port, final WriterHelper writerHelper)
	{
		this.port = port;
		this.writerHelper = writerHelper;
		LoggingHelper.debug().log("\ntry to start Writer (Client) with port " + this.port + " in 1 second ...");
		while (true ) {
			try {
				Thread.sleep(1000);
				// try to establish the connection to the python server (wrapper)
				this.clientSocket = new Socket("127.0.0.1", this.port);
				LoggingHelper.debug().log("writer (Client) started ... \n");
				this.dataOutputStream = this.clientSocket.getOutputStream();
				break;
			} catch (UnknownHostException e) {
				e.printStackTrace();
			} catch (IOException e) {
				LoggingHelper.error().log("error establishing connection to port %d, reason: %s", this.port, e.getMessage());
				LoggingHelper.warn().log("could not establish connection to port %d, retrying in 1 second ...", this.port);
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
		}
		LoggingHelper.debug().log("InputSocketWriter:: done ...\n  WriterHelper: "+ this.writerHelper + ",  this.dataOutputStream: " + this.dataOutputStream);

	}

	@Override
	public void writeValueMessage(ValueMessage valueMessage) {
    	try {
    		this.writerHelper.writeMessage(valueMessage, InterfaceType.SOCKET, null, this.dataOutputStream );
//			// SOCKETS: response can be used later
//    		String response = this.inFromWrapper.readLine();
//	        System.out.println("response: " + response);
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	@Override
	public void writeSyncMessage(SyncMessage syncMessage) {
		try {
			this.writerHelper.writeToStream( this.dataOutputStream, syncMessage );
//			// SOCKETS: response can be used later
//			String response = this.inFromWrapper.readLine();
//			System.out.println("response: " + response);
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

}
