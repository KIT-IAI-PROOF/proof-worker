/*
 * Copyright (c) 2025-2026
 * Karlsruhe Institute of Technology - Institute for Automation and Applied Informatics (IAI)
 */
package edu.kit.iai.webis.proofworker.services;

import java.util.StringJoiner;

import org.springframework.stereotype.Service;

import edu.kit.iai.webis.proofutils.LoggingHelper;
import edu.kit.iai.webis.proofutils.MessageBuilder;
import edu.kit.iai.webis.proofutils.exception.CannotPerformStepException;
import edu.kit.iai.webis.proofutils.helper.StepSizeDefinitionHelper;
import edu.kit.iai.webis.proofutils.message.BaseMessage;
import edu.kit.iai.webis.proofutils.message.IMessage;
import edu.kit.iai.webis.proofutils.message.MessageType;
import edu.kit.iai.webis.proofutils.message.NotifyMessage;
import edu.kit.iai.webis.proofutils.message.SyncMessage;
import edu.kit.iai.webis.proofutils.model.SimulationPhase;
import edu.kit.iai.webis.proofutils.model.SimulationStatus;
import edu.kit.iai.webis.proofutils.model.SimulationStrategy;
import edu.kit.iai.webis.proofutils.model.SyncStrategy;
import edu.kit.iai.webis.proofutils.wrapper.Block;
import edu.kit.iai.webis.proofutils.wrapper.Workflow;
import edu.kit.iai.webis.proofworker.config.WorkerConfig;
import edu.kit.iai.webis.proofworker.model.ModelInputInterface;
import edu.kit.iai.webis.proofworker.util.StatusHelper;
import edu.kit.iai.webis.proofworker.util.StringTemplates;

@Service
public class SyncController {

    private final ValueController valueController;  // to write values
    private final NotifyController notifyController;  // to share communicationPoints etc.
    private final WorkerConfig workerConfig;
    private final Block block;
    private final Workflow workflow;

    private StepSizeDefinitionHelper stepSizeDefinitionHelper;
    private Integer communicationPoint = 0;
    private long processTimeOut = 4;
    private final BaseMessage baseMessage;

    private final SyncStrategy currentSyncStrategy;
    private SimulationPhase currentSimulationPhase = SimulationPhase.CREATE;
    private ModelInputInterface currentModelInputInterface;
	private StatusHelper statusHelper;

	boolean retryMessageSent = false;

    public SyncController(
            final Block block,
            final Workflow workflow,
            final WorkerConfig workerConfig,
            final NotifyController notifyController,
            final ValueController valueController
    ) {
        this.workerConfig = workerConfig;
        this.workflow = workflow;
        this.block = block;
        this.stepSizeDefinitionHelper = new StepSizeDefinitionHelper(this.workflow.getStepBasedConfig(),
                this.workerConfig.getLocalBlockId());

        LoggingHelper.debug().log("Block '%s' %s for SYNC ...", block.getName(),
                block.getSyncStrategy() == SyncStrategy.WAIT_FOR_SYNC ? "waits" : "does not wait");
        this.processTimeOut = this.workerConfig.getProcessTimeout();

        this.notifyController = notifyController;
        this.valueController = valueController;

        this.notifyController.setStepSizeDefinitionHelper( this.stepSizeDefinitionHelper );
        this.valueController.setStepSizeDefinitionHelper( this.stepSizeDefinitionHelper );

        this.statusHelper = new StatusHelper();
        this.notifyController.setStatusHelper(this.statusHelper);

        this.baseMessage = new BaseMessage();
        this.baseMessage.setLocalBlockId(this.workerConfig.getLocalBlockId());
        this.baseMessage.setGlobalBlockId(this.workerConfig.getGlobalBlockId());
        this.baseMessage.setExecutionId(this.workerConfig.getWorkflowExecutionId());
        this.baseMessage.setWorkflowId(this.workerConfig.getWorkflowUuid());

        this.currentSyncStrategy = this.block.getSyncStrategy();
    }


	/**
	 * process a SYNC message coming from the orchestrator
	 * @param syncMessage the SYNC message
	 */
	public void processStep(final SyncMessage syncMessage) {
		if( this.currentSimulationPhase != syncMessage.getSimulationPhase() ) {
			this.currentSimulationPhase = syncMessage.getSimulationPhase();
			this.currentModelInputInterface = ModelInputInterface.getModelInputInterface(this.currentSimulationPhase);
		}

        /*
         * set the local and global block id. Other attributes are not necessary
         */
        syncMessage.setGlobalBlockId(this.workerConfig.getGlobalBlockId());
        syncMessage.setLocalBlockId(this.workerConfig.getLocalBlockId());

        this.communicationPoint = syncMessage.getCommunicationPoint();
        this.notifyController.setCommunicationPoint(this.communicationPoint);
        this.valueController.setCommunicationPoint(this.communicationPoint);

        LoggingHelper.debug()
        .log(" ----------- Received SYNC -> Processing step, Phase=%s, communicationPoint=%d", this.currentSimulationPhase, this.communicationPoint);

        if (this.currentModelInputInterface == null) {
            /**
             * there exists no wrapper: send ERROR message to orchestrator and return
             */
            final NotifyMessage notifyMessage = (NotifyMessage) this.createMessage(MessageType.NOTIFY,
                    this.currentSimulationPhase, SimulationStatus.ERROR_STEP);
            notifyMessage.setErrorText("There is NO ModelInputInterface in the local Map for phase " + this.currentSimulationPhase);
            LoggingHelper.error().log("+++ There is NO ModelInputInterface in the local Map for phase " + this.currentSimulationPhase + ", " +
                    "sending NOTIFY to Orchestrator: " + notifyMessage);
            this.notifyController.sendNotifyMessage(notifyMessage);
            return;
        }

        switch (this.currentSimulationPhase) {
            case INIT -> {
            	// all values are already written to the wrapper. Only send a sync INIT message for init processing
            	LoggingHelper.debug().log("Sending SYNC message to the wrapper ... ");
                this.writeSyncMessageToStream(syncMessage, this.currentModelInputInterface);
		    }
		case EXECUTE -> {
			if( SyncStrategy.INSTANT == this.currentSyncStrategy ) {
				// do nothing, because notify message for this step is probably already received from the wrapper and sent to the orchestrator
				return;
			}
			/**
			 * If there is an SYNC that should not be forwarded to the wrapper, return with notifyMessage
			 */
			switch (this.statusHelper.getStatus()) {
				case EXECUTION_FINISHED, FINALIZED, ERROR_INIT, ERROR_STEP, ERROR_FINALIZE, ABORTED, STOPPED, SHUT_DOWN -> {
					final NotifyMessage notifyMessage = (NotifyMessage) this.createMessage(MessageType.NOTIFY, this.currentSimulationPhase, SimulationStatus.ERROR_STEP);
					final String errMsg = "The given status " + this.statusHelper.getStatus() + " of the last model message does not allow to forward a further SYNC message to the model!";
					notifyMessage.setErrorText(errMsg);
					LoggingHelper.error().log(errMsg);
					this.notifyController.sendNotifyMessage(notifyMessage);
					return;
				}
				default -> {}  // do nothing
			}

			/**
			 * If the step for this communicationPoint cannot be performed:
			 * send Notify with EXECUTION_FINISHED or EXECUTION_STEP_FINISHED
			 */
			try {
				if ( !this.stepSizeDefinitionHelper.canPerformStep(this.communicationPoint)) { // delivers a simple boolean
					LoggingHelper.debug().log("CANNOT perform Step %d  due to defined step sizes => returning NotifyMessage ... ", this.communicationPoint);

					final NotifyMessage notifyMessage =
							(NotifyMessage) this.createMessage(MessageType.NOTIFY, this.currentSimulationPhase, SimulationStatus.EXECUTION_STEP_FINISHED);
					this.notifyController.sendNotifyMessage(notifyMessage);
					return;
				}
			} catch (CannotPerformStepException e) {
				// normal end, the current communication point becomes greater than the end point
				final NotifyMessage notifyMessage = (NotifyMessage) this.createMessage(MessageType.NOTIFY, this.currentSimulationPhase, SimulationStatus.EXECUTION_FINISHED);
                LoggingHelper.error().log(e.getMessage());
                this.notifyController.sendNotifyMessage(notifyMessage);
                return;
			}

			LoggingHelper.debug().log("performing Step %d  due to defined step sizes ... (Workflow SimulationStrategy=%s)", this.communicationPoint,
					this.workflow.getSimulationStrategy());

                // if the block has no (dynamic) inputs for EXECUTE, write the SYNC message
                if (this.block.getInputNameMappings(this.currentSimulationPhase).isEmpty()) {
                    LoggingHelper.debug().log("The BLOCK '%s' has NO dynamic Inputs from other blocks, writing SYNC " +
                            "to STREAM (Wrapper), phase=%s", this.block.getName(), SimulationPhase.EXECUTE);
                    this.writeSyncMessageToStream(syncMessage, this.currentModelInputInterface);
                    this.currentModelInputInterface.clearValues();
                }
				else {
                    /*
                    * the block has dynamic inputs
                    * BUT its StepBasedOutputWrapper has not enough values for the dynamic Inputs
                    * Now it depends on the simulation strategy how to continue
                    */
                    if( LoggingHelper.isLevelDebugOrTrace() ){
                            LoggingHelper.trace()
                            .log("The BLOCK '%s' has dynamic Inputs for values from other blocks AND the ModelInputInterface may have the suitable values "
                                            + "for the dynamic Inputs.   Values: %s", this.block.getName(), this.currentModelInputInterface.getValues() );
                            LoggingHelper.printHashMapContents( this.currentModelInputInterface.getValues(), System.out, "WrapperValues");
                    }

                    switch (this.workflow.getSimulationStrategy()) {

                        case IGNORE -> {
                            LoggingHelper.debug().log("Simulation Strategy is IGNORE, and not each required Input has its VALUE => ignoring SYNC and sending Notify to Orchestrator ...");
                            final NotifyMessage notifyMessage = (NotifyMessage) this.createMessage(MessageType.NOTIFY, this.currentSimulationPhase, SimulationStatus.READY);
                            this.notifyController.sendNotifyMessage(notifyMessage);
                        }
                        case WAIT_AND_CONTINUE, WAIT_AND_RETRY -> {
                            /**
                             * wait until all required values arrived (from other blocks)
                             */
                            // Wait at least one cycle:
                            // Remark: prevent processTimeOut=0: it would mean to expect all values to be available
                            // when processStep is called.
                            long processTimeOut = Math.max(1, this.processTimeOut); // [s]
                            long elapsedTime = 0;
                            long timeToWait = processTimeOut * 1000;   // [ms]
                            long waitInterval = 500;    // [ms]
                            int defValWaits = 15;  // number of waiting cycles before default values are used
                            int numWaits = 0;   // number of waiting cycles

                            LoggingHelper.debug().log("Time2wait: %d, ElapsedTime: %d,  WaitInterval: %d,  all needed values given: %s  ,  Block SYNC strategy: %s", timeToWait, elapsedTime, waitInterval,
                            		this.currentModelInputInterface.allNeededValuesGiven(), this.block.getSyncStrategy() );

                            //if( this.currentModelInputInterface.allNeededValuesGiven() ) {
                            //	this.writeSyncMessageToStream(syncMessage, this.currentModelInputInterface);
                            //	return;
                            //}
                            //else {
                            //	while( elapsedTime < timeToWait ) {
                                do {
                            		if( this.currentModelInputInterface.allNeededValuesGiven() ) {
                            			this.valueController.sendValuesToWrapper(this.currentSimulationPhase, this.communicationPoint);
                            			this.writeSyncMessageToStream(syncMessage, this.currentModelInputInterface);
                            			return;
                            		}
                            		else { // not all required values are given => wait
                            	    	StringJoiner sj = new StringJoiner(",", " [", "] " + this.workflow.getSimulationStrategy() );
                            	    	this.currentModelInputInterface.getMissingRequiredInputValues().forEach(s -> sj.add(s));

                                        LoggingHelper.debug().log(StringTemplates.BLOCK_IS_WAITING_FOR_REQUIRED_INPUT
                            				.formatted(this.workerConfig.getLocalBlockId(), waitInterval)
                            				+ " [ms]:  " + sj.toString()
                            				+ ",  elapsed time=" + elapsedTime);
                                        try {
                            				Thread.sleep(waitInterval);
                            				numWaits++;
                            			} catch (InterruptedException e) {
                            				e.printStackTrace();
                            				break;
                            			}
                            			if( numWaits > defValWaits ) {
                            				LoggingHelper.debug().log("%d waiting cycles passed, try to use default values ... ", numWaits);
											this.currentModelInputInterface.useDefaultValues();
											if( this.currentModelInputInterface.allNeededValuesGiven() ) {
										    	LoggingHelper.debug().log("Default values found and used, all values given, sending them to the wrapper ... ");
												this.valueController.sendValuesToWrapper(this.currentSimulationPhase, this.communicationPoint);
												this.writeSyncMessageToStream(syncMessage, this.currentModelInputInterface);
												return;
											}
										}

                            			elapsedTime += waitInterval;
                            		}
                            	} while( elapsedTime < timeToWait );

                            	if( this.workflow.getSimulationStrategy() == SimulationStrategy.WAIT_AND_CONTINUE ) {
                            		this.sendErrorNotifyMesssage();
                            	}
                            	else if( this.workflow.getSimulationStrategy() == SimulationStrategy.WAIT_AND_RETRY ) {
                            		if( this.retryMessageSent ) {
                            			// already sent a unique RETRY back to the orchestrator
                            			this.sendErrorNotifyMesssage();
                            			this.retryMessageSent = false;
                            		}
                            		else {
                            			LoggingHelper.debug().log("SimulationStrategy=WAIT_AND_RETRY::   Value not present for block '%s', sending RETRY to Orchestrator and waiting for SYNC ", this.block.getId());
                            			final NotifyMessage notifyMessage = (NotifyMessage) this.createMessage(MessageType.NOTIFY, this.currentSimulationPhase, SimulationStatus.RETRY);
                            			this.retryMessageSent = true;
                            			this.notifyController.sendNotifyMessage(notifyMessage);
                            		}
                            	}
                            //}

	                    }
                        case LATEST -> {
                            LoggingHelper.info()
                            .log(StringTemplates.VALUE_FOR_IOINTERFACE_NOT_PRESENT_USING_LATEST_VALUE.formatted(this.block.getId()) + ", SimulationStrategy=LATEST" );
                            if (this.currentModelInputInterface.getPreviousValues().size() > 0) {
                                this.valueController.sendPreviousValuesToWrapper(this.currentSimulationPhase, this.communicationPoint);
                                this.writeSyncMessageToStream(syncMessage, this.currentModelInputInterface);
                            }
                            else {
                                LoggingHelper.debug().log("Values for Wrapper not present and no latest values in the current step, => sending NotifyMessage ERROR message to Orchestrator ...");
                                final NotifyMessage notifyMessage = (NotifyMessage) this.createMessage(MessageType.NOTIFY, this.currentSimulationPhase, SimulationStatus.ERROR_STEP);
                                notifyMessage.setErrorText("Values for Wrapper not present and no latest values in the current step");
                                this.notifyController.sendNotifyMessage(notifyMessage);
                            }
                        }

                        default ->
                                throw new RuntimeException("Unexpected value: " + this.workflow.getSimulationStrategy());
                    }  // switch (this.workflow.getSimulationStrategy()) {
                }  // } else {
            }  // case EXECUTE

            case FINALIZE -> {
                // finalize ...
                final SyncMessage newSyncMessage = (SyncMessage) this.createMessage(MessageType.SYNC,
                        this.currentSimulationPhase, null);

                LoggingHelper.debug().messageObject(newSyncMessage)
                        .log("FINALIZED step: writing SYNC to STREAM (Wrapper), phase=" + SimulationPhase.FINALIZE);

                this.writeSyncMessageToStream(newSyncMessage, this.currentModelInputInterface);
            }
            case SHUTDOWN -> {
                final SyncMessage newSyncMessage = (SyncMessage) this.createMessage(MessageType.SYNC,
                        this.currentSimulationPhase, null);
                final ModelInputInterface shutdownMii =
                        ModelInputInterface.getModelInputInterface(SimulationPhase.EXECUTE);
                LoggingHelper.debug().log("sending SHUTDOWN Snyc Message to Wrapper");

                this.writeSyncMessageToStream(newSyncMessage, shutdownMii);
                LoggingHelper.debug().log("SHUTDOWN Snyc Message sent ...");
            }
            default -> throw new IllegalArgumentException("Unexpected value: " + this.currentSimulationPhase);
        }

        LoggingHelper.printHashMapContents(this.currentModelInputInterface.getValues(), System.out,
            "processStep:  Stored values of SimulationPhase  " + this.currentSimulationPhase + "  for Block " + this.workerConfig.getLocalBlockId() + ":");
    }


    private void writeSyncMessageToStream(SyncMessage syncMessage, ModelInputInterface modelInputInterface) {
    	modelInputInterface.resetValueCounter();
        modelInputInterface.getWriter().writeSyncMessage(syncMessage);
        LoggingHelper.debug().log("SYNC " + syncMessage.getSimulationPhase() + " Message sent, waiting for " +
                "NotifyMessage from Wrapper...");
    }

    private void sendErrorNotifyMesssage() {
    	StringJoiner sj = new StringJoiner(",", "Not each required Input has its VALUE during timeout (", ")  Simulation Strategy: " + this.workflow.getSimulationStrategy() );
    	this.currentModelInputInterface.getMissingRequiredInputValues().forEach(s -> sj.add(s));

    	LoggingHelper.debug().log(sj.toString());
		final NotifyMessage notifyMessage = (NotifyMessage) this.createMessage(MessageType.NOTIFY, this.currentSimulationPhase, SimulationStatus.ERROR_STEP);
		notifyMessage.setErrorText(sj.toString());
		this.notifyController.sendNotifyMessage(notifyMessage);
    }

    private IMessage createMessage(MessageType messageType, SimulationPhase simulationPhase, SimulationStatus blockStatus) {
        return MessageBuilder.init(messageType)
                .copyOf(this.baseMessage)
                .communicationPoint(this.communicationPoint)
                .simulationPhase(simulationPhase)
                .blockStatus(blockStatus)
                .build();
    }

}
