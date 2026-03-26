/*
 * Copyright (c) 2025-2026
 * Karlsruhe Institute of Technology - Institute for Automation and Applied Informatics (IAI)
 */
package edu.kit.iai.webis.proofworker.services;

import org.springframework.stereotype.Service;

import edu.kit.iai.webis.proofutils.LoggingHelper;
import edu.kit.iai.webis.proofutils.MessageBuilder;
import edu.kit.iai.webis.proofutils.helper.NameHelper;
import edu.kit.iai.webis.proofutils.helper.StepSizeDefinitionHelper;
import edu.kit.iai.webis.proofutils.io.MQNotifyProducer;
import edu.kit.iai.webis.proofutils.message.BaseMessage;
import edu.kit.iai.webis.proofutils.message.IMessage;
import edu.kit.iai.webis.proofutils.message.MessageType;
import edu.kit.iai.webis.proofutils.message.NotifyMessage;
import edu.kit.iai.webis.proofutils.model.SimulationStatus;
import edu.kit.iai.webis.proofutils.model.SimulationPhase;
import edu.kit.iai.webis.proofutils.service.ConsumerManager;
import edu.kit.iai.webis.proofutils.wrapper.Block;
import edu.kit.iai.webis.proofutils.wrapper.Workflow;
import edu.kit.iai.webis.proofworker.config.WorkerConfig;
import edu.kit.iai.webis.proofworker.util.ReaderHelper;
import edu.kit.iai.webis.proofworker.util.StatusHelper;

@Service
public class NotifyController {

    private final Block block;
	private final ConsumerManager consumerManager;
    private final WorkerConfig workerConfig;
    private final Workflow workflow;
    private final MQNotifyProducer notifyProducer;

    private final BaseMessage baseMessage;
    private Integer communicationPoint = 0;
	private StepSizeDefinitionHelper stepSizeDefinitionHelper;  // for startPoint, endPoint etc.
	private int endPoint;
    /**
     * mark the BlockStatus passed by the wrapper.
     * This attribute is set by the {@link ReaderHelper} when a {@link NotifyMessage} arrives from the wrapper
     */
	private String notifyQueueName = null;

	private StatusHelper statusHelper;

	public NotifyController(
			final Block block,
			final Workflow workflow,
            final WorkerConfig workerConfig,
            final MQNotifyProducer notifyProducer,
			final ConsumerManager consumerManager
	) {
        this.block = block;
        this.workflow = workflow;
        this.workerConfig = workerConfig;
        this.notifyProducer = notifyProducer;
        this.consumerManager = consumerManager;
        this.baseMessage = new BaseMessage();
        this.baseMessage.setLocalBlockId(this.workerConfig.getLocalBlockId());
        this.baseMessage.setGlobalBlockId(this.workerConfig.getGlobalBlockId());
        this.baseMessage.setExecutionId(this.workerConfig.getWorkflowExecutionId());
        this.baseMessage.setWorkflowId(this.workerConfig.getWorkflowUuid());
        this.notifyQueueName = NameHelper.getNotifyQueueName(this.workerConfig.getWorkflowExecutionId(), this.block);
	}

	public void setStatusHelper(StatusHelper statusHelper) {
		this.statusHelper = statusHelper;
	}

	public void setStepSizeDefinitionHelper( StepSizeDefinitionHelper ssdh ) {
		this.stepSizeDefinitionHelper = ssdh;
		this.endPoint = this.stepSizeDefinitionHelper.getEndPoint();
	}

	/**
	 * process a NotifyMessage coming from the wrapper
	 * @param notifyMessage the message
	 */
	public void processNotifyMessage( NotifyMessage notifyMessage ) {

        SimulationStatus blockStatus = notifyMessage.getBlockStatus();
		this.statusHelper.setStatus(blockStatus);

        if (blockStatus == SimulationStatus.ERROR_INIT ||
                blockStatus == SimulationStatus.ERROR_STEP ||
                blockStatus == SimulationStatus.ERROR_FINALIZE) {
            LoggingHelper.error().log("Notify ERROR message arrived: " + notifyMessage.getErrorText());
			LoggingHelper.printStarBordered(blockStatus.toString() + " : " + notifyMessage.getErrorText() );
        }

        else if (blockStatus.equals(SimulationStatus.EXECUTION_STEP_FINISHED)) {

            int sum = this.communicationPoint + this.stepSizeDefinitionHelper.getCommunicationStepSize(this.communicationPoint);
            LoggingHelper.trace().log("BlockStatus=" + blockStatus + " BEFORE checking whether the execution is " +
				"finished");

            if (sum > this.endPoint) {
                LoggingHelper.info().log( "(CP=%d, StepSize=%d) CP+StepSize=%d > %d (endPoint): sending EXECUTION_FINISHED to Orchestrator ...",
                		this.communicationPoint, this.communicationPoint+this.stepSizeDefinitionHelper.getCommunicationStepSize(this.communicationPoint), sum, this.endPoint );
                blockStatus = SimulationStatus.EXECUTION_FINISHED;
            } else {
            	LoggingHelper.trace().log( "CommunicationPoint=%d, CommunicationStepSize=%d, sum=%d < endPoint=%d",
            			this.communicationPoint, this.stepSizeDefinitionHelper.getCommunicationStepSize(this.communicationPoint), sum, this.endPoint);
            }

            LoggingHelper.trace().log("BlockStatus=" + blockStatus + " AFTER checking whether the execution is " +
                    "finished");
        }

        final IMessage ntfyMessage = MessageBuilder
                .init(MessageType.NOTIFY)
				.copyOf(this.baseMessage)
                .blockStatus(blockStatus)
                .communicationPoint(this.communicationPoint)
                .simulationPhase(notifyMessage.getSimulationPhase())
                .errorText(notifyMessage.getErrorText())
                .build();

        this.sendNotifyMessage((NotifyMessage)ntfyMessage);

        LoggingHelper.info()
        	.messageObject(ntfyMessage)
            .log("ActionController: NOTIFY sent to orchestrator (Status: %s, CP=%d (local: %d)\n)", blockStatus, notifyMessage.getCommunicationPoint(), this.communicationPoint );

        if( blockStatus.equals(SimulationStatus.SHUT_DOWN )){
        	this.shutdown();
        	return;
        }
	}

	/**
	 * send a {@link NotifyMessage} to the orchestrator
	 *
	 * @param notifyMessage the message
	 */
	public void sendNotifyMessage( NotifyMessage notifyMessage ) {
		notifyMessage.setCommunicationPoint(this.communicationPoint);
		this.notifyProducer.sendToQueue( this.notifyQueueName, notifyMessage);
		this.statusHelper.setStatus(notifyMessage.getBlockStatus());
	}

	/**
	 * set the current communicationPoint
	 * @param communicationPoint
	 */
	public void setCommunicationPoint(Integer communicationPoint) {
		this.communicationPoint = communicationPoint;
	}

	private void shutdown() {
		try {
            final IMessage notifyMessage = MessageBuilder.init(MessageType.NOTIFY)
                    .blockStatus(SimulationStatus.SHUT_DOWN)
                    .copyOf(this.baseMessage)
                    .simulationPhase(SimulationPhase.SHUTDOWN)
                    .communicationPoint(this.communicationPoint)
                    .build();

            this.sendNotifyMessage((NotifyMessage) notifyMessage);
			// give half a second to send the message
			LoggingHelper.info().log("shutting down Block '" + this.workerConfig.getGlobalBlockId() + "' ... waiting 2000ms to stop consumers\n");
			Thread.currentThread().join(2000);
			this.consumerManager.stopConsumers();
			LoggingHelper.info().log("shutting down ... ");
			System.exit(0);
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
	}

}