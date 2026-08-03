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
 * Service for publishing block status updates to the monitoring service via RabbitMQ.
 * This allows the monitoring UI to display real-time execution progress.
 */
@Service
public class MonitoringPublisher {

    private final RabbitTemplate rabbitTemplate;
    private final WorkerConfig workerConfig;

    @Value("${proof.monitoring.exchange.name:execution-status-exchange}")
    private String monitoringExchangeName;

    @Value("${proof.monitoring.routing.key:execution.status.update}")
    private String monitoringRoutingKey;

    @Value("${proof.monitoring.enabled:true}")
    private boolean monitoringEnabled;

    public MonitoringPublisher(RabbitTemplate rabbitTemplate, WorkerConfig workerConfig) {
        this.rabbitTemplate = rabbitTemplate;
        this.workerConfig = workerConfig;
    }

    /**
     * Publishes a block status update to the monitoring queue.
     * The update is sent asynchronously and failures do not affect workflow execution.
     *
     * @param status the new simulation status
     * @param cp the current communication point
     * @param errorMessage optional error message (can be null)
     */
    public void publishStatusUpdate(SimulationStatus status, Integer cp, String errorMessage) {
        if (!monitoringEnabled) {
            LoggingHelper.trace().log("Monitoring is disabled, skipping status update");
            return;
        }

        if (status == null) {
            LoggingHelper.warn().log("Cannot publish null status to monitoring");
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

            rabbitTemplate.convertAndSend(monitoringExchangeName, monitoringRoutingKey, update);

            LoggingHelper.debug().log(
                    "Published status update to monitoring: exchange=%s, routingKey=%s, blockId=%s, status=%s",
                    monitoringExchangeName, monitoringRoutingKey, update.getBlockId(), update.getStatus()
            );
        } catch (Exception e) {
            LoggingHelper.error().log(
                    "Failed to publish status update to monitoring: %s",
                    e.getMessage()
            );
        }
    }
}
