/*
 * Copyright (c) 2025-2026
 * Karlsruhe Institute of Technology - Institute for Automation and Applied Informatics (IAI)
 */
package edu.kit.iai.webis.proofworker.services;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.StringJoiner;

import org.springframework.stereotype.Service;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import edu.kit.iai.webis.proofutils.Colors;
import edu.kit.iai.webis.proofutils.LoggingHelper;
import edu.kit.iai.webis.proofutils.MessageBuilder;
import edu.kit.iai.webis.proofutils.exception.MappingException;
import edu.kit.iai.webis.proofutils.helper.StepSizeDefinitionHelper;
import edu.kit.iai.webis.proofutils.io.MQValueProducer;
import edu.kit.iai.webis.proofutils.message.BaseMessage;
import edu.kit.iai.webis.proofutils.message.IMessage;
import edu.kit.iai.webis.proofutils.message.MessageType;
import edu.kit.iai.webis.proofutils.message.NotifyMessage;
import edu.kit.iai.webis.proofutils.message.ValueMessage;
import edu.kit.iai.webis.proofutils.model.SimulationPhase;
import edu.kit.iai.webis.proofutils.model.SimulationStatus;
import edu.kit.iai.webis.proofutils.model.SyncStrategy;
import edu.kit.iai.webis.proofutils.wrapper.Block;
import edu.kit.iai.webis.proofutils.wrapper.Input;
import edu.kit.iai.webis.proofutils.wrapper.Output;
import edu.kit.iai.webis.proofworker.config.WorkerConfig;
import edu.kit.iai.webis.proofworker.exception.TypeMismatchException;
import edu.kit.iai.webis.proofworker.exception.ValueConfigException;
import edu.kit.iai.webis.proofworker.model.InputQueueNameMapping;
import edu.kit.iai.webis.proofworker.model.ModelInputInterface;
import edu.kit.iai.webis.proofworker.model.OutputQueueNameMapping;
import edu.kit.iai.webis.proofworker.util.MappingHelper;
import edu.kit.iai.webis.proofworker.util.ValueHelper;

/**
 * Controller to manage ValueMessages. They can come from <br>
 * <ul>
 * <li>the rabbitmq queue (orchestrator(INIT) or any block (EXECUTE) => only save values and wait for SYNC
 * <li>the model (wrapper) => write the values to the rabbitmq exchanges (to other blocks) => method processValueMessageFromWrapper()
 * </ul>
 */
@Service
public class ValueController {

    private final Block block;
    private final WorkerConfig workerConfig;
    private final MappingHelper mappingHelper;
    private final MQValueProducer valueProducer;
    private final NotifyController notifyController;  // to send (error) messages to the orchestrator.

    private final BaseMessage baseMessage;
    private Integer communicationPoint = 0;
	private StepSizeDefinitionHelper stepSizeDefinitionHelper;  // for startPoint, endPoint etc.
	private int startPoint, endPoint;

	private ValueMessage baseValueMessage;

    private final EnumMap<SimulationPhase, List<OutputQueueNameMapping>> outputQueueNameMappingPerPhase = new EnumMap<SimulationPhase, List<OutputQueueNameMapping>>(
    		Map.of(
    				SimulationPhase.INIT, new ArrayList<OutputQueueNameMapping>(),
    				SimulationPhase.EXECUTE, new ArrayList<OutputQueueNameMapping>(),
    				SimulationPhase.FINALIZE, new ArrayList<OutputQueueNameMapping>()
    		));

    private final EnumMap<SimulationPhase, Map<String, InputQueueNameMapping>> inputQueueNameMappingPerPhase = new EnumMap<SimulationPhase, Map<String, InputQueueNameMapping>>(
    		Map.of(
    				SimulationPhase.CREATE, new HashMap<String, InputQueueNameMapping>(),
    				SimulationPhase.INIT, new HashMap<String, InputQueueNameMapping>(),
    				SimulationPhase.EXECUTE, new HashMap<String, InputQueueNameMapping>(),
    				SimulationPhase.FINALIZE, new HashMap<String, InputQueueNameMapping>()
    		));

    private final EnumMap<SimulationPhase, ModelInputInterface> modelInputInterfaces = new EnumMap<>(SimulationPhase.class);

    private final List<String> requiredInputNames = new ArrayList<String>();

    public ValueController(
			final Block block,
            final WorkerConfig workerConfig,
            final NotifyController notifyController,
            final MQValueProducer valueProducer,
            final MappingHelper mappingHelper
	) {
        this.block = block;
        this.workerConfig = workerConfig;
        this.notifyController = notifyController;
        this.mappingHelper = mappingHelper;
        this.valueProducer = valueProducer;
        this.baseMessage = new BaseMessage();
        this.baseMessage.setLocalBlockId(this.workerConfig.getLocalBlockId());
        this.baseMessage.setGlobalBlockId(this.workerConfig.getGlobalBlockId());
        this.baseMessage.setExecutionId(this.workerConfig.getWorkflowExecutionId());
        this.baseMessage.setWorkflowId(this.workerConfig.getWorkflowUuid());
        this.baseValueMessage = (ValueMessage) MessageBuilder.init(MessageType.VALUE)
				.globalBlockId(this.workerConfig.getGlobalBlockId())
				.localBlockId(this.workerConfig.getLocalBlockId())
				.workflowId(this.workerConfig.getWorkflowUuid())
				.executionId(this.workerConfig.getWorkflowExecutionId())
				.build();
        this.block.getRequiredDynamicInputs().forEach(rdi -> {
        	this.requiredInputNames.add(rdi.getName());
        });
	}

	public void setStepSizeDefinitionHelper( StepSizeDefinitionHelper ssdh ) {
		this.stepSizeDefinitionHelper = ssdh;
		this.endPoint = this.stepSizeDefinitionHelper.getEndPoint();
		this.startPoint = this.stepSizeDefinitionHelper.getStartPoint();
	}


	/**
     * process values originating from wrapper and send them to the corresponding value queues (RabbitMQ exchanges)
     *
	 * @param valueMessage value message containing the {@link SimulationPhase}, the data to be sent (a JsonObject), and
	 */
	public void processValueMessageFromWrapper(final ValueMessage valueMessage )
	{
    	// for each Output (OQNMapping) map the Output name and value
		SimulationPhase simulationPhase = valueMessage.getSimulationPhase();
		Map<String, String> outputNameMappings = this.block.getOutputNameMappings(simulationPhase);
		if( outputNameMappings == null ) {
        	LoggingHelper.debug().log("there are no output mappings for this ValueMessage: >>%s<<", valueMessage );
			return;
		}

    	this.baseValueMessage.setSimulationPhase(simulationPhase);
    	this.baseValueMessage.setCommunicationPoint(valueMessage.getCommunicationPoint());
    	JsonObject data = (JsonObject)valueMessage.getData();

        this.getOutputQueueNameMappings(simulationPhase).forEach(
        		(final OutputQueueNameMapping outputQueueNameMapping) -> {
        			Output output = outputQueueNameMapping.getOutput();
        			final String outputDataType = output.getType().getValue();

        			outputNameMappings.forEach((final String outputVarName, final String modelVarName) -> {
        	            if (!outputQueueNameMapping.getOutput().getName().equals(outputVarName)) {
        	                if (!outputQueueNameMapping.getOutput().getName().equals(modelVarName)) {
        	                    return;
        	                }
        	            }
        	            final JsonElement outputVarValue = data.get(modelVarName);
        	            try {
        	            	var outputData = this.mappingHelper.mapOutputValue(outputVarValue, outputDataType);
        	            	LoggingHelper.debug().log("OutputData: " + outputData + ", outputVarValue: " + outputVarValue
        	            			+ ", outputType: " + outputDataType );
        	            	this.baseValueMessage.setData(outputData);
        	        		this.valueProducer.sendToExchange(outputQueueNameMapping.getQueue(), outputQueueNameMapping.getQueue(),
        	        				this.baseValueMessage);
        	        		LoggingHelper.debug().messageObject(this.baseValueMessage).data(data)
        	        				.log("Sent " + outputDataType + " value to Exchange. Data: >>" + data + "<<\n");

						} catch (Exception e) {
							LoggingHelper.error().exception(e).log("ERROR mapping element!");
						}
        			});
        		});
    }

    /**
     * send the values for the previous step to the wrapper
     * @param simulationPhase the current {@link SimulationPhase}
     * @param communicationPoint the current communication point
     */
    public void sendPreviousValuesToWrapper(SimulationPhase simulationPhase, Integer communicationPoint ) {
    	this.sendValuesToWrapper(simulationPhase, communicationPoint, false);
    }

    /**
     * send values to the wrapper
     * @param simulationPhase the current {@link SimulationPhase}
     * @param communicationPoint the current communication point
     */
    public void sendValuesToWrapper(SimulationPhase simulationPhase, Integer communicationPoint ) {
    	this.sendValuesToWrapper(simulationPhase, communicationPoint , true);
    }

    private void sendValuesToWrapper(SimulationPhase simulationPhase, Integer communicationPoint, boolean current )
    {
    	this.communicationPoint = communicationPoint;
    	// für jeden Output (OQNMapping) mappe den Output-Namen und -Wert
    	this.baseValueMessage.setSimulationPhase(simulationPhase);

        final ModelInputInterface modelInputInterface = ModelInputInterface.getModelInputInterface(simulationPhase);
        Map<String, Object> values2send = current ? modelInputInterface.getValues() : modelInputInterface.getPreviousValues();

        final var resultingData = new JsonObject();

        values2send.forEach( (modelVarName, value) -> {
        	final Object inputVarValue = values2send.get(modelVarName);
        	if( this.requiredInputNames.contains(modelVarName) && inputVarValue == null ) {
        		final var message = "given mapping has no value for input '" + modelVarName + "' , but it is required!";
        		LoggingHelper.error().log(message);
        		throw new MappingException(message);
        	}
        	try {
        		Input input = this.getInputQueueNameMappings(simulationPhase).get(modelVarName).getInput();
        		String inputDataType = input.getType().getValue();
    			this.mappingHelper.mapInputValue(resultingData, inputVarValue, inputDataType, modelVarName);
    			LoggingHelper.debug().log("mapped element: value: '%s' , inputDataType: %s , targetName: %s",
    					inputVarValue, inputDataType, modelVarName);
    		} catch (Exception e) {
    			LoggingHelper.error().exception(e).log("ERROR mapping element!");
    		}
        });

    	LoggingHelper.debug().log("send Values to Wrapper after having mapped them. -> resultingData: %s \n".formatted(resultingData));
    	this.baseValueMessage.setData(resultingData);

    	this.writeValues(modelInputInterface, this.communicationPoint);
    	modelInputInterface.prepareNextStep();
    	LoggingHelper.trace().messageObject(this.baseValueMessage).data(resultingData)
    	.log("Sent values to Wrapper. Data: >>" + resultingData + "<<\n");
    }

    private void sendInstantValueToWrapper(SimulationPhase simulationPhase, ModelInputInterface modelInputInterface, String modelVarName, Object value)
    {

    	// für jeden Output (OQNMapping) mappe den Output-Namen und -Wert
    	this.baseValueMessage.setSimulationPhase(simulationPhase);
    	final var resultingData = new JsonObject();

		Input input = this.getInputQueueNameMappings(simulationPhase).get(modelVarName).getInput();
		String inputDataType = input.getType().getValue();
		if( value == null  && input.isRequired() ) {
			final var message = "given mapping has no value for the input, but it is required!";
			LoggingHelper.error().log(message);
			throw new MappingException(message);
		}
		try {
			this.mappingHelper.mapInputValue(resultingData, value, inputDataType, input.getModelVarName());
			LoggingHelper.debug().log("mapped element: sourceValue: %s , inputDataType: %s , targetName: %s -> %s",
					value, inputDataType, input.getModelVarName(), resultingData);
		} catch (Exception e) {
			LoggingHelper.error().exception(e).log("ERROR mapping element!");
		}

    	LoggingHelper.debug().log("send Value %s instantly to Wrapper after having mapped it. -> resultingData: %s \n", modelVarName, resultingData);
    	this.baseValueMessage.setData(resultingData);

    	this.writeValues(modelInputInterface, this.communicationPoint);
    	modelInputInterface.addToSentValues(modelVarName, value);
    	LoggingHelper.trace().messageObject(this.baseValueMessage).data(resultingData)
    	.log("Sent value to Wrapper. Data: >>" + resultingData + "<<\n");
    }



    /**
     * add an {@link InputQueueNameMapping} to the ValueController
     *
     * @param inputQueueNameMapping the {@link InputQueueNameMapping} to be added.
     */
    public void addInputQueueNameMapping(InputQueueNameMapping inputQueueNameMapping) {

    	final Input input = inputQueueNameMapping.getInput();
    	if( input == null ) {
    		LoggingHelper.error().withBorder().log("No Input available for Queue %s",inputQueueNameMapping.getQueue());
    		return;
    	}
    	else {
    		final SimulationPhase phase = input.getSimulationPhase();
    		LoggingHelper.debug().withBorder().log("adding input queue name mapping for phase '%s':   Input: '%s', Queue: '%s'",
    				phase, input.getName(), inputQueueNameMapping.getQueue());
//
//    		if( LoggingHelper.isLevelDebugOrTrace() ) {
//    			System.out.println("VC::addInputQueueNameMapping: adding mapping for phase " + phase + ", Contents of Mapping list: ");
//    			this.inputQueueNameMappingPerPhase.get(phase).values().forEach(im -> {
//    				System.out.println("--> VC: Queue=" + im.getQueue() + ", Input=" + im.getInput().getName() + ", Input-Phase=" + im.getInput().getSimulationPhase());
//    			});
//    		}
    		this.inputQueueNameMappingPerPhase.get(phase).put(input.getModelVarName(), inputQueueNameMapping);
    	}
    }

    /**
     * get a list of {@link InputQueueNameMappings} for a given {@link SimulationPhase}
     *
     * @param phase the given {@link SimulationPhase}
     * @return a list of found {@link InputQueueNameMappings}s, may be empty
     */
    public Map<String, InputQueueNameMapping> getInputQueueNameMappings(SimulationPhase phase) {
        return this.inputQueueNameMappingPerPhase.get(phase);
    }


    /**
     * add an {@link OutputQueueNameMapping} to the ValueController
     *
     * @param outputQueueNameMapping the {@link IOSink} to be added.
     *               If it already exists, identified by it's id, it will override the existing one
     */
    public void addOutputQueueNameMapping(OutputQueueNameMapping outputQueueNameMapping) {
    	this.outputQueueNameMappingPerPhase.get(outputQueueNameMapping.getOutput().getSimulationPhase()).add(outputQueueNameMapping);
    }

    /**
     * get a list of {@link OutputQueueNameMappings} for a given {@link SimulationPhase}
     *
     * @param phasethe given {@link SimulationPhase}
     * @return a list of found {@link OutputQueueNameMapping}s, may be empty

     */
    public List<OutputQueueNameMapping> getOutputQueueNameMappings(SimulationPhase phase) {
        return this.outputQueueNameMappingPerPhase.get(phase);
    }

    /**
     * Save the INIT value(s) received from orchestrator (via RabbitMQ) in temporary buffer, to send them to the wrapper when
     * tact sync is received or when values should be forwarded immediately to the wrapper
     *
     * @param rawValue    Value to save, can be JSON, string or number
     * @param staticInput Target input of this value transmission
     */
//    public synchronized void processInitValue(Object rawValue, final Input staticInput) {//throws TypeMismatchException, ValueConfigException {
//
//    	final SimulationPhase simulationPhase = staticInput.getSimulationPhase();
//    	LoggingHelper.debug().log("processing INIT Value '%s' (Input='%s', type=%s, modelVarName: %s, default value: %s)",
//    			rawValue, staticInput.getName(), staticInput.getType().getValue(), staticInput.getModelVarName(), staticInput.getDefaultValue());
//
//		// Use the default value for static inputs if no rawValue is present
//    	ValueHelper.Result result = ValueHelper.getValue(rawValue != null ? rawValue : staticInput.getDefaultValue(), staticInput.getType().getValue());
//
//    	final ModelInputInterface modelInputInterface = ModelInputInterface.getModelInputInterface(simulationPhase);
//
//    	modelInputInterface.addToValues(staticInput.getModelVarName(), result.value());  // check there whether all needed values are given
//
//    	LoggingHelper.debug().messageObject(this.baseMessage)
//    	.log("Saved received %s value in Phase=%s for input  %s (ModelVarname=%s),  value=%s  \t\t#vals=%d",
//    			result.typeName(), simulationPhase, staticInput.getName(), staticInput.getModelVarName(), result.value(),
//    			modelInputInterface.getValues().size() );
//
//    	if( modelInputInterface.allNeededValuesGiven() ) {
//    		LoggingHelper.debug().log("===> VC:: modelInputInterface.allNeededValuesGiven(): " + modelInputInterface.allNeededValuesGiven());
//
//    		this.sendValuesToWrapper(simulationPhase, this.communicationPoint);
//    	}
//    }

    public synchronized void processInitValues( final Map<Input, Object> staticInputValues ) { //throws TypeMismatchException, ValueConfigException {

    	final ModelInputInterface modelInputInterface = ModelInputInterface.getModelInputInterface(SimulationPhase.INIT);
    	final List<String> msgList = new ArrayList<String>();

    	staticInputValues.forEach( (input, value) -> {
    		LoggingHelper.debug().log("processing INIT Value '%s' (Input='%s', type=%s, modelVarName: %s, default value: %s)",
    				value, input.getName(), input.getType().getValue(), input.getModelVarName(), input.getDefaultValue());

    		try {
    			// Use the default value for static inputs if no rawValue is present
    			ValueHelper.Result result = ValueHelper.getValue(value != null ? value : input.getDefaultValue(), input.getType().getValue());
    			modelInputInterface.addToValues(input.getModelVarName(), result.value());  // check there whether all needed values are given
			} catch (TypeMismatchException | ValueConfigException e) {
				msgList.add("input '%s': %s".formatted(input.getName(), e.getMessage()));
			}

    	});

    	if( msgList.size() > 0 ) {
    		final StringJoiner sj = new StringJoiner("\n", "Error processing (scanning) values for following inputs:\n", "" );
    		msgList.forEach(s -> sj.add(s));
    		LoggingHelper.error().log(sj.toString());
    		IMessage notifyMsg = MessageBuilder.init(MessageType.NOTIFY)
    				.errorText(sj.toString())
    				.simulationPhase(SimulationPhase.INIT)
    				.localBlockId(this.block.getIndex())
    				.globalBlockId(this.block.getName())
    				.blockStatus(SimulationStatus.ERROR_INIT)
    				.build();
    		this.notifyController.sendNotifyMessage((NotifyMessage)notifyMsg);
    	}

    	if( modelInputInterface.allNeededValuesGiven() ) {
    		LoggingHelper.debug().log("===> VC:: modelInputInterface.allNeededValuesGiven(): " + modelInputInterface.allNeededValuesGiven());

    		this.sendValuesToWrapper(SimulationPhase.INIT, this.communicationPoint);
    	}
    }

    /**
     * Save the value(s) received from another block (via RabbitMQ) in temporary buffer, to send them to the wrapper when
     * tact sync is received or when values should be forwarded immediately to the wrapper
     *
     *
     * @param rawValue    Value to save, can be JSON, string or number
     * @param targetInput Target input of this value transmission
     */
    public synchronized void processValue(final ValueMessage valueMessage, final Input targetInput) throws TypeMismatchException, ValueConfigException {

        var rawValue = valueMessage.getData();
        if( rawValue == null ) {
        	rawValue = ( this.communicationPoint == this.startPoint ? targetInput.getStartValue() : targetInput.getDefaultValue() );
        }
        Objects.requireNonNull(rawValue, "value is not given, neither by start value nor by default value!");
    	final String modelVarName = targetInput.getModelVarName();
    	final SimulationPhase simulationPhase = targetInput.getSimulationPhase();
    	LoggingHelper.debug().log("processing Value '%s' (simulationPhase= %s, type=%s, Input=%s, modelVarName: %s)",
    			rawValue, simulationPhase, targetInput.getType().getValue(), targetInput.getName(), modelVarName);

		// Use the default value for processing if no rawValue is present
		// REFACTOR: if CP=startPt: startValue else default!

    	ValueHelper.Result result = ValueHelper.getValue(rawValue, targetInput.getType().getValue());

    	final ModelInputInterface modelInputInterface = ModelInputInterface.getModelInputInterface(simulationPhase);

    	modelInputInterface.addToValues(modelVarName, result.value());  // check there whether all needed values are given

    	LoggingHelper.debug().messageObject(this.baseMessage)
    	.log("Saved received value (%s)  \t\t#vals=%d", result.value(), modelInputInterface.getValues().size() );

    	/**
    	 * some blocks can provide values but the current block can have a different start point
    	 */
    	if( this.communicationPoint < this.startPoint ) {
    		LoggingHelper.debug().log("CP (%d) < StartPoint (%d), doing nothing", this.communicationPoint, this.startPoint);
    		return;
    	}
    	else if( modelInputInterface.allNeededValuesGiven() ) {
    		LoggingHelper.debug().log("===> VC:: modelInputInterface.allNeededValuesGiven(): " + modelInputInterface.allNeededValuesGiven());

    		if( SimulationPhase.EXECUTE.equals(simulationPhase)){

    			switch (this.block.getSyncStrategy()) {
    			case INSTANT -> {
    				// write values immediately to the wrapper
    				LoggingHelper.debug().messageObject(this.baseMessage).log("Forwarding value '%s' due to SyncStrategy INSTANT", result.value());
    				this.sendInstantValueToWrapper(simulationPhase, modelInputInterface, modelVarName, result.value());
    				modelInputInterface.addToSentValues(modelVarName, result.value());
    			}
    			case ALL_VALUES, WAIT_FOR_SYNC -> {
    				// if all values are given:
    				this.sendValuesToWrapper(simulationPhase, this.communicationPoint);
    			}
//  	        			case WAIT_FOR_SYNC -> {
//  	        				this.sendValuesToWrapper(simulationPhase, this.communicationPoint);
//  	        			}
    			}
    		}
    	}
    }
    /**
     *
     * @param modelInputInterface
     */
    public boolean areAllInputValuesGiven(ModelInputInterface modelInputInterface, SimulationPhase phase) {
    	return modelInputInterface.getValues().keySet().containsAll(this.block.getInputNameMappings(phase).keySet());
   }

    /**
     *
     * @param modelInputInterface
     */
    public boolean areAllRequiredInputValuesGiven(ModelInputInterface modelInputInterface) {
    	return modelInputInterface.getValues().keySet().containsAll(this.requiredInputNames);
    }

    /**
     *
     * @param modelInputInterface
     */
    public boolean areAllRequiredInputValuesSent(ModelInputInterface modelInputInterface) {
    	if( LoggingHelper.isLevelDebugOrTrace() ) {
    		LoggingHelper.printHashMapContents(modelInputInterface.getSentValues(), System.out, "\n============\nsent Values:");
    		System.out.println("Required Values:");
    		this.requiredInputNames.forEach(r -> {
    			System.out.println(r);
    		});
    	}
    	return modelInputInterface.getSentValues().keySet().containsAll(this.requiredInputNames);
    }

    /**
     *
     * @param modelInputInterface
     */
    public boolean areAllInputValuesSent(ModelInputInterface modelInputInterface) {
    	if( LoggingHelper.isLevelDebugOrTrace() ) {
    		LoggingHelper.printHashMapContents(modelInputInterface.getSentValues(), System.out, "\n============\nsent Values:");
    		System.out.println("Required Values:");
    		this.requiredInputNames.forEach(r -> {
    			System.out.println(r);
    		});
    	}
    	return modelInputInterface.getSentValues().keySet().containsAll(this.requiredInputNames);
    }

    private void writeValues( ModelInputInterface modelInputInterface, Integer communicationPoint) {
    	final ValueMessage valueMessage = (ValueMessage) MessageBuilder
    			.init(MessageType.VALUE)
    			.copyOf(this.baseMessage)
    			.data(this.baseValueMessage.getData())
    			.simulationPhase(modelInputInterface.getSimulationPhase())
    			.communicationPoint(communicationPoint == null ? 0 : communicationPoint)
    			.build();

    	LoggingHelper.trace().messageObject(valueMessage)
    	.log("writing workflow phase value(s) to " + modelInputInterface.getInterfaceType().toString() + ". phase: " + valueMessage.getSimulationPhase() + ", " +
    			"Values: " + valueMessage.getData());

    	modelInputInterface.getWriter().writeValueMessage(valueMessage);
    	// REFACTOR: correct to clear only when INSTANT ? => values are always stored for RETRY in clearValues()
    	if( this.block.getSyncStrategy() != SyncStrategy.INSTANT ) {
    		modelInputInterface.clearValues();
    	}
    	LoggingHelper.debug().messageColor(Colors.ANSI_RED).log("values written, values cleared");
    }

    private void writePreviousValues( ModelInputInterface modelInputInterface) {
    	final ValueMessage valueMessage = (ValueMessage) MessageBuilder
    			.init(MessageType.VALUE)
    			.copyOf(this.baseMessage)
    			.data(modelInputInterface.getPreviousValues())
    			.simulationPhase(modelInputInterface.getSimulationPhase())
    			.communicationPoint(this.communicationPoint == null ? 0 : this.communicationPoint)
    			.build();

       LoggingHelper.trace().messageObject(valueMessage)
        .log("writing workflow phase value(s) to " + modelInputInterface.getInterfaceType().toString() + ". phase: " + valueMessage.getSimulationPhase() + ", " +
        		"Values: " + valueMessage.getData());

        modelInputInterface.getWriter().writeValueMessage(valueMessage);
        modelInputInterface.clearValues();
        LoggingHelper.debug().messageColor(Colors.ANSI_RED).log("values written, values cleared");
    }

    /**
     * set the communication point. This method is called by the {@link SyncController} when a SYNC message arrives
     * (see {@link SyncController#processStep(edu.kit.iai.webis.proofutils.message.SyncMessage)})
     * @param communicationPoint the communication point
     */
    public void setCommunicationPoint(Integer communicationPoint) {
		this.communicationPoint = communicationPoint;
	}
}
