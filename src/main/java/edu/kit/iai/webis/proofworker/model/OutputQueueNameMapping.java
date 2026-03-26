/*
 * Copyright (c) 2025-2026
 * Karlsruhe Institute of Technology - Institute for Automation and Applied Informatics (IAI)
 */
package edu.kit.iai.webis.proofworker.model;

import edu.kit.iai.webis.proofutils.wrapper.Output;

public class OutputQueueNameMapping {

    private Output output;
    private String queueName;
    
    public OutputQueueNameMapping() {}
    
	public OutputQueueNameMapping(Output output, String queueName) {
		super();
		this.output = output;
		this.queueName = queueName;
	}

	public Output getOutput() {
		return this.output;
	}

	public String getQueue() {
		return this.queueName;
	}
	
	@Override
	public String toString() {
		return this.getClass().getSimpleName() + ":  output: " +  output.getName() + ", Queue name: " + output.getName();
	}

}
