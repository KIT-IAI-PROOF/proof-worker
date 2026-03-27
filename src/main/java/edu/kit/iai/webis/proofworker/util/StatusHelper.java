/*
 * Copyright (c) 2025-2026
 * Karlsruhe Institute of Technology - Institute for Automation and Applied Informatics (IAI)
 */
package edu.kit.iai.webis.proofworker.util;

import edu.kit.iai.webis.proofutils.model.SimulationStatus;
import edu.kit.iai.webis.proofworker.services.NotifyController;
import edu.kit.iai.webis.proofworker.services.SyncController;

/**
 * a simple class to store the current model status sent by the model.
 * it is used by {@link NotifyController} and the {@link SyncController}
 */
public class StatusHelper {

	private SimulationStatus status;

	public StatusHelper() {}

	public SimulationStatus getStatus() {
		return this.status;
	}

	public void setStatus(SimulationStatus status) {
		this.status = status;
	}

}
