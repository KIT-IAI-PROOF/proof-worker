/*
 * Copyright (c) 2026
 * Karlsruhe Institute of Technology - Institute for Automation and Applied Informatics
 */

package edu.kit.iai.webis.proofworker.util;

import edu.kit.iai.webis.proofutils.model.SimulationStatus;

/**
 * Utility class for mapping internal SimulationStatus to monitoring service status strings.
 */
public class StatusMapper {

    /**
     * Maps internal SimulationStatus to monitoring service status strings.
     * Both SimulationStatus and ESimulationStatus enums share most values,
     * so direct name mapping works.
     *
     * @param status the internal SimulationStatus
     * @return the status name as string for the monitoring service
     */
    public static String toMonitoringStatus(SimulationStatus status) {
        if (status == null) {
            return "UNKNOWN";
        }
        return status.name();
    }
}
