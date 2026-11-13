/*
 * Copyright (c) 2026
 * Karlsruhe Institute of Technology - Institute for Automation and Applied Informatics
 */

package edu.kit.iai.webis.proofworker.services;

import edu.kit.iai.webis.proofutils.LoggingHelper;
import edu.kit.iai.webis.proofutils.model.SimulationStatus;
import edu.kit.iai.webis.proofworker.config.WorkerConfig;
import edu.kit.iai.webis.proofworker.model.BlockStatusUpdate;
import edu.kit.iai.webis.proofworker.util.StatusMapper;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Instant;

/**
 * Service for publishing block status updates to the execution-specific status queue.
 * Each execution has its own stream queue named: "status.execution.<execution-id>"
 * 
 * Status updates are sent asynchronously and failures do not affect workflow execution.
 */
@Service
public class MonitoringPublisher {

    private final RabbitTemplate rabbitTemplate;
    private final WorkerConfig workerConfig;

    @Value("${proof.status-updates.enabled:true}")
    private boolean statusUpdatesEnabled;

    public MonitoringPublisher(RabbitTemplate rabbitTemplate, WorkerConfig workerConfig) {
        this.rabbitTemplate = rabbitTemplate;
        this.workerConfig = workerConfig;
    }

    /**
     * Publishes a block status update to the execution-specific status queue.
     * The update is sent asynchronously and failures do not affect workflow execution.
     *
     * @param status the new simulation status
     * @param cp the current communication point
     * @param errorMessage optional error message (can be null)
     */
    public void publishStatusUpdate(SimulationStatus status, Integer cp, String errorMessage) {
        if (!statusUpdatesEnabled) {
            LoggingHelper.trace().log("Status updates are disabled, skipping status update");
            return;
        }

        if (status == null) {
            LoggingHelper.warn().log("Cannot publish null status");
            return;
        }

        try {
            BlockStatusUpdate update = BlockStatusUpdate.builder()
                    .executionId(workerConfig.getWorkflowExecutionId())
                    .blockId(workerConfig.getGlobalBlockId())
                    .status(StatusMapper.toMonitoringStatus(status))
                    .cp(cp)
                    .message(errorMessage)
                    .timestamp(Instant.now())
                    .build();

            String queueName = getStatusQueueName(workerConfig.getWorkflowExecutionId());
            rabbitTemplate.convertAndSend(queueName, update);

            LoggingHelper.debug().log(
                    "Published status update to queue: queue=%s, blockId=%s, status=%s",
                    queueName, update.getBlockId(), update.getStatus()
            );
        } catch (Exception e) {
            LoggingHelper.error().log(
                    "Failed to publish status update: %s",
                    e.getMessage()
            );
        }
    }

    /**
     * Get the status queue name for the given execution.
     * 
     * @param executionId the execution ID
     * @return the queue name: "status.execution.<execution-id>"
     */
    private static String getStatusQueueName(String executionId) {
        return "status.execution." + executionId;
    }
}
