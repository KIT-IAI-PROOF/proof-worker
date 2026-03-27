/*
 * Copyright (c) 2025-2026
 * Karlsruhe Institute of Technology - Institute for Automation and Applied Informatics (IAI)
 */
package edu.kit.iai.webis.proofworker.services;

import org.springframework.stereotype.Service;

import com.google.common.collect.HashBiMap;
import com.google.common.collect.BiMap;

import edu.kit.iai.webis.proofutils.LoggingHelper;
import edu.kit.iai.webis.proofutils.MessageBuilder;
import edu.kit.iai.webis.proofutils.exception.CannotPerformStepException;
import edu.kit.iai.webis.proofutils.helper.StepSizeDefinitionHelper;
import edu.kit.iai.webis.proofutils.message.BaseMessage;
import edu.kit.iai.webis.proofutils.message.IMessage;
import edu.kit.iai.webis.proofutils.message.MessageType;
import edu.kit.iai.webis.proofutils.message.NotifyMessage;
import edu.kit.iai.webis.proofutils.message.SyncMessage;
import edu.kit.iai.webis.proofutils.message.ValueMessage;
import edu.kit.iai.webis.proofutils.model.SimulationStatus;
import edu.kit.iai.webis.proofutils.model.SimulationPhase;
import edu.kit.iai.webis.proofutils.model.SyncStrategy;
import edu.kit.iai.webis.proofutils.wrapper.Block;
import edu.kit.iai.webis.proofutils.wrapper.Workflow;
import edu.kit.iai.webis.proofworker.config.WorkerConfig;
import edu.kit.iai.webis.proofworker.model.ModelInputInterface;
import edu.kit.iai.webis.proofworker.util.StatusHelper;
import edu.kit.iai.webis.proofworker.util.StringTemplates;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

@Service
public class SyncController {

    private final ValueController valueController;  // to write values
    private final NotifyController notifyController;  // to share communicationPoints etc.
    private final WorkerConfig workerConfig;
    private final Block block;
    private final Workflow workflow;

    private StepSizeDefinitionHelper stepSizeDefinitionHelper;
    private Integer communicationPoint = 0;
    private Integer communicationStepSize = 1;
    private final int startPoint, endPoint;
    private long processTimeOut = 2;
    private final BaseMessage baseMessage;
    // REFACTOR: is this necessary?
    private boolean waitingForNotifyMessage = false;

    private SimulationPhase currentSimulationPhase = SimulationPhase.CREATE;
    private ModelInputInterface currentModelInputInterface;
    private SimulationStatus workerStatus;

	private StatusHelper statusHelper;

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
        this.startPoint = this.stepSizeDefinitionHelper.getStartPoint();
        this.endPoint = this.stepSizeDefinitionHelper.getEndPoint();
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

		LoggingHelper.trace()
		.log(" ----------- Received SYNC -> Processing step, Phase=%s, communicationPoint=%d", this.currentSimulationPhase, this.communicationPoint);

        /*
         * set the local and global block id. Other attributes are not necessary
         */
        syncMessage.setGlobalBlockId(this.workerConfig.getGlobalBlockId());
        syncMessage.setLocalBlockId(this.workerConfig.getLocalBlockId());

        this.communicationPoint = syncMessage.getCommunicationPoint();
        this.notifyController.setCommunicationPoint(this.communicationPoint);
        this.communicationStepSize = this.stepSizeDefinitionHelper.getCommunicationStepSize(this.communicationPoint);

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
                // write all static values and a sync INIT value via the queues to the wrapper
                if (LoggingHelper.isLevelDebugOrTrace()) {
                    LoggingHelper.debug().log("INIT step:  writing VALUES to STREAM (Wrapper) for Block %d, phase=%s"
                            , this.workerConfig.getLocalBlockId(), syncMessage.getSimulationPhase());
                    LoggingHelper.printHashMapContents(this.currentModelInputInterface.getValues(), System.out,
                            "INIT step:  Values of SimulationPhase  " + this.currentSimulationPhase + "  for Block " + this.workerConfig.getLocalBlockId() + ":");
                }

                final ValueMessage initValueMessage = (ValueMessage) MessageBuilder
                        .init(MessageType.VALUE)
                        .copyOf(this.baseMessage)
                        .simulationPhase(this.currentSimulationPhase)
                        .communicationPoint(this.communicationPoint)
                        .build();

                // Check which static inputs are used as initial value for non-static inputs.
                Map<String, String> staticInputList = this.block.getInputNameMappings(this.currentSimulationPhase);
                Map<String, String> nonStaticInputList = this.block.getInputNameMappings(SimulationPhase.EXECUTE);
                BiMap<String, String> nonStaticInputListBidirectional = HashBiMap.create(nonStaticInputList);
                Map<String, Object> allStaticValues = this.currentModelInputInterface.getValues();
                ModelInputInterface executeMii = ModelInputInterface.getModelInputInterface(SimulationPhase.EXECUTE);
                LoggingHelper.debug().log("Static input list for phase %s: %s", this.currentSimulationPhase, staticInputList);
                LoggingHelper.debug().log("Non-static input list for phase %s: %s", SimulationPhase.EXECUTE, nonStaticInputList);
                LoggingHelper.debug().log("Bidirectional non-static input list for phase %s: %s", SimulationPhase.EXECUTE, nonStaticInputListBidirectional);
                LoggingHelper.debug().log("All static values for phase %s: %s", this.currentSimulationPhase, allStaticValues);
                LoggingHelper.debug().log("mii-values for phase %s: %s", SimulationPhase.EXECUTE, executeMii.getValues());
                allStaticValues.forEach((inputName, value) -> {;
                    String modelVarname = staticInputList.get(inputName);
                    // If the modelVarname of this input is used in a non-static input for EXECUTE phase, add the
                    // value to the ModelInputInterface.
                    String execInputName = nonStaticInputListBidirectional.inverse().get(modelVarname);
                    if (execInputName != null) {
                        LoggingHelper.debug().log("Input '%s' is used as initial value for modelVarname '%s', " +
                            "adding '%s' to ModelInputInterface with value '%s'", inputName, modelVarname,
                            execInputName, value);
                        executeMii.addToValues(execInputName, value);
                    }
                });
                LoggingHelper.debug().log("Adjusted mii-values for phase %s: %s", SimulationPhase.EXECUTE, executeMii.getValues());

                LoggingHelper.printHashMapContents(this.currentModelInputInterface.getValues(), System.out, "writing " +
                    "Values (valueController.sendValuesToWrapper) and send a SYNC to STREAM (Wrapper), phase=" + SimulationPhase.INIT);
                this.valueController.sendValuesToWrapper(this.currentSimulationPhase, this.communicationPoint);
                LoggingHelper.printHashMapContents(this.currentModelInputInterface.getValues(), System.out, "Sent values to wrapper");
                this.writeSyncMessageToStream(syncMessage, this.currentModelInputInterface);
		    }
		case EXECUTE -> {
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
			LoggingHelper.debug().log("performing Step %d  due to defined step sizes ...", this.communicationPoint);

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
                        case WAIT_AND_CONTINUE -> {
                            /**
                             * wait until all required values arrived (from other blocks)
                             */
                            long elapsedTime = 0;
                            long timeToWait = this.processTimeOut * 1000;
                            long startTime = System.currentTimeMillis();

                            // REFACTOR: better error text
                            LoggingHelper.debug().log(StringTemplates.BLOCK_IS_WAITING_FOR_REQUIRED_INPUT
                                        .formatted(this.workerConfig.getLocalBlockId(), this.processTimeOut));


                            while( elapsedTime < timeToWait ) {

                                elapsedTime = (System.currentTimeMillis() - startTime);

                                // first check whether all required input values are given
                                if (this.areAllCurrentValuesNotNull()
                                    && (this.currentModelInputInterface.getValues().size() == this.block.getNumberOfDynamicInputs()
                                    || this.valueController.areAllRequiredInputValuesGiven(this.currentModelInputInterface))) {
                                    LoggingHelper.debug().log("Each required input value given => send the values" +
                                            " to the Wrapper");
                                    this.valueController.sendValuesToWrapper(this.currentSimulationPhase,
                                            this.communicationPoint);

                                    // REFACTOR: move to canPerformStep() in StepSizeDefinition
                                    if( this.communicationPoint >= this.startPoint )
                                    {
                                        if( this.block.getSyncStrategy() == SyncStrategy.WAIT_FOR_SYNC )
                                        {
                                            this.writeSyncMessageToStream(syncMessage, this.currentModelInputInterface);
                                        }
                                    }
                                    break; // leave while loop
                                }
	                        } // while
                            boolean timedOut = elapsedTime >= timeToWait;
                            if (timedOut) {
                                LoggingHelper.debug().log("Not each required Input has its VALUE during timeout => sending NotifyMessage ERROR message to Orchestrator ...");
                                final NotifyMessage notifyMessage = (NotifyMessage) this.createMessage(MessageType.NOTIFY, this.currentSimulationPhase, SimulationStatus.ERROR_STEP);
                                notifyMessage.setErrorText("Not each required Input has its VALUE during timeout");
                                this.notifyController.sendNotifyMessage(notifyMessage);
                            }
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
                        case WAIT_AND_RETRY -> {
                            // FEAT: implement
                            LoggingHelper.info()
                            .log("SimulationStrategy=WAIT_AND_RETRY::   Value not present for block '%s', waiting for SYNC from Orchestrator", this.block.getId());

                            final NotifyMessage notifyMessage = (NotifyMessage) this.createMessage(MessageType.NOTIFY, this.currentSimulationPhase, SimulationStatus.READY);
                            this.notifyController.sendNotifyMessage(notifyMessage);
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
        modelInputInterface.getWriter().writeSyncMessage(syncMessage);
        LoggingHelper.debug().log("SYNC " + syncMessage.getSimulationPhase() + " Message sent, waiting for " +
                "NotifyMessage from Wrapper...");
        this.waitingForNotifyMessage = true;
    }


    private IMessage createMessage(MessageType messageType, SimulationPhase simulationPhase, SimulationStatus blockStatus) {
        return MessageBuilder.init(messageType)
                .copyOf(this.baseMessage)
                .communicationPoint(this.communicationPoint)
                .simulationPhase(simulationPhase)
                .blockStatus(blockStatus)
                .build();
    }

    private boolean areAllCurrentValuesNotNull() {
        boolean areAllCurrentValuesNotNull = false;
        final Map<String, Object> currentInputs = this.currentModelInputInterface.getValues();

        synchronized (currentInputs) {
            if (currentInputs.size() > 0) {
                areAllCurrentValuesNotNull = true;
                LoggingHelper.trace().log("currentInputs: %s", currentInputs);
                for (String inputName : currentInputs.keySet()) {
                    if (currentInputs.get(inputName) == null || inputName == null || inputName.isEmpty()) {
                        areAllCurrentValuesNotNull = false;
                        LoggingHelper.trace().log("No Value for current Input '%s' is given for Block '%s'",
                                inputName, this.block.getName());
                        break;
                    } else {
                        LoggingHelper.trace().log("Value for current Input '%s' is given for Block '%s', areAllCurrentValuesNotNull: %s",
                                inputName, this.block.getName(), areAllCurrentValuesNotNull);
                    }
                }
            }
        }
        return areAllCurrentValuesNotNull;
    }
}
