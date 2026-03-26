/*
 * Copyright (c) 2025-2026
 * Karlsruhe Institute of Technology - Institute for Automation and Applied Informatics (IAI)
 */
package edu.kit.iai.webis.proofworker.model;

import edu.kit.iai.webis.proofutils.wrapper.Input;

public class InputQueueNameMapping {

    private Input input;
    private String queueName;
    
    public InputQueueNameMapping() {}

    public InputQueueNameMapping(Input input, String queueName) {
		super();
		this.input = input;
		this.queueName = queueName;
	}

	public Input getInput() {
		return this.input;
	}

	public String getQueue() {
		return this.queueName;
	}
	
	@Override
	public String toString() {
		return this.getClass().getSimpleName() + ":  input: " +  input.getName() + ", Queue name: " + this.queueName;
	}
}
