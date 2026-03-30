/*
 * Copyright (c) 2025-2026
 * Karlsruhe Institute of Technology - Institute for Automation and Applied Informatics (IAI)
 */
package edu.kit.iai.webis.proofworker.services;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

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
import edu.kit.iai.webis.proofutils.message.MessageType;
import edu.kit.iai.webis.proofutils.message.ValueMessage;
import edu.kit.iai.webis.proofutils.model.CommunicationType;
import edu.kit.iai.webis.proofutils.model.SimulationPhase;
import edu.kit.iai.webis.proofutils.model.SyncStrategy;
import edu.kit.iai.webis.proofutils.wrapper.Block;
import edu.kit.iai.webis.proofutils.wrapper.Input;
import edu.kit.iai.webis.proofutils.wrapper.Output;
import edu.kit.iai.webis.proofworker.config.WorkerConfig;
import edu.kit.iai.webis.proofworker.exception.TypeMismatchException;
import edu.kit.iai.webis.proofworker.model.InputQueueNameMapping;
import edu.kit.iai.webis.proofworker.model.ModelInputInterface;
import edu.kit.iai.webis.proofworker.model.OutputQueueNameMapping;
import edu.kit.iai.webis.proofworker.util.MappingHelper;
import edu.kit.iai.webis.proofworker.util.StringTemplates;
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

    private final EnumMap<SimulationPhase, List<InputQueueNameMapping>> inputQueueNameMappingPerPhase = new EnumMap<SimulationPhase, List<InputQueueNameMapping>>(
    		Map.of(
    				SimulationPhase.CREATE, new ArrayList<InputQueueNameMapping>(),
    				SimulationPhase.INIT, new ArrayList<InputQueueNameMapping>(),
    				SimulationPhase.EXECUTE, new ArrayList<InputQueueNameMapping>(),
    				SimulationPhase.FINALIZE, new ArrayList<InputQueueNameMapping>()
    		));

    private final EnumMap<SimulationPhase, ModelInputInterface> modelInputInterfaces = new EnumMap<>(SimulationPhase.class);

    private final List<String> requiredInputNames = new ArrayList<String>();

    public ValueController(
			final Block block,
            final WorkerConfig workerConfig,
            final MQValueProducer valueProducer,
            final MappingHelper mappingHelper
	) {
        this.block = block;
        this.workerConfig = workerConfig;
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


//	@SuppressWarnings("unchecked")
	public void processValueMessageFromWrapper( ValueMessage valueMessage ) {

		SimulationPhase sPhase = valueMessage.getSimulationPhase();
		Map<String, String> outputNameMappings = this.block.getOutputNameMappings(sPhase);

		if( outputNameMappings != null ) {

			this.sendValuesToExchanges(
					sPhase,
					(JsonObject)valueMessage.getData(),
					outputNameMappings);
		}
	}

	/**
     * send values to the corresponding value queues (RabbitMQ exchanges)
     *
	 * @param simulationPhase the {@link SimulationPhase}
	 * @param data	the data to be sent (a JsonObject)
	 * @param outputNameMappings the mappings for the input/output pairs
	 */
    public void sendValuesToExchanges(SimulationPhase simulationPhase,
                                      JsonObject data,
                                      Map<String, String> outputNameMappings) {
    	// for each Output (OQNMapping) map the Output name and value
    	this.baseValueMessage.setSimulationPhase(simulationPhase);

        this.getOutputQueueNameMappings(simulationPhase).forEach(
        		(final OutputQueueNameMapping outputQueueNameMapping) -> {
        			Output output = outputQueueNameMapping.getOutput();
        			final String outputDataType = output.getType().getValue();

        			outputNameMappings.forEach((final String target, final String source) -> {
        	            if (!outputQueueNameMapping.getOutput().getName().equals(target)) {
        	                if (!outputQueueNameMapping.getOutput().getName().equals(source)) {
        	                    return;
        	                }
        	            }
        	            final JsonElement sourceValue = data.get(source);
        	            try {
        	            	var outputData = this.mappingHelper.mapOutputValue(sourceValue, outputDataType);
        	            	LoggingHelper.debug().log("OutputData: " + outputData + ", sourceValue: " + sourceValue
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

    public void sendPreviousValuesToWrapper(SimulationPhase simulationPhase, Integer communicationPoint ) {
    	this.sendValuesToWrapper(simulationPhase, communicationPoint, false);
    }

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

		Map<String, String> inputNameMappings = this.block.getInputNameMappings(simulationPhase);
		LoggingHelper.debug().log("sendValuesToWrapper: Mapping Names: %s", inputNameMappings);
		LoggingHelper.debug().log("sendValuesToWrapper: Values to send: %s", values2send);

        final var resultingData = new JsonObject();
    	this.getInputQueueNameMappings(simulationPhase).forEach(
			(final InputQueueNameMapping inputQueueNameMapping) -> {
				Input input = inputQueueNameMapping.getInput();
				final String inputDataType = input.getType().getValue();
				String inputName = input.getName();
				String inputModelVarName = input.getModelVarName();
				LoggingHelper.debug().log("sendValuesToWrapper::IQNM: inputName=%s, modelVarName=%s", inputName, inputModelVarName);

		        inputNameMappings.forEach((final String name, final String modelVarName) -> {
					LoggingHelper.debug().log("sendValuesToWrapper::IQNM: Processing element (%s, %s)",
						name, modelVarName);
					if( inputName.equals(modelVarName)  ||   inputName.equals(name)) {
		        		final Object sourceValue = values2send.get(name);
		        		// check whether a required value is present:
		        		if( sourceValue == null  && input.isRequired() ) {
		        			final var message = ("sendValuesToWrapper::IQNM: given mapping for '%s' (modelVarName: " +
								"'%s') has no value for the input, but it is required!\nValues2Send: " + values2send).formatted(name,
								inputModelVarName);
		        			LoggingHelper.error().log(message);
		        			throw new MappingException(message);
		        		}

		        		try {
		        			this.mappingHelper.mapInputValue(resultingData, sourceValue, inputDataType, modelVarName);
		        			LoggingHelper.debug().log("sendValuesToWrapper::IQNM: mapped element: sourceValue: %s , " +
									"inputDataType: %s , modelVarName: %s -> %s",
		        					sourceValue, inputDataType, modelVarName, resultingData);
		        		} catch (Exception e) {
		        			LoggingHelper.error().exception(e).log("ERROR mapping element!");
		        		}
		        	}
				});

			});
    	LoggingHelper.debug().log("send Values to Wrapper after having mapped them. -> resultingData: %s \n".formatted(resultingData));
    	this.baseValueMessage.setData(resultingData);

    	this.writeValues(modelInputInterface, this.communicationPoint);
    	LoggingHelper.trace().messageObject(this.baseValueMessage).data(resultingData)
    	.log("Sent values to Wrapper. Data: >>" + resultingData + "<<\n");
    }



    /**
     * add an {@link InputQueueNameMapping} to the ValueController
     *
     * @param inputQueueNameMapping the {@link InputQueueNameMapping} to be added.
     */
    public void addInputQueueNameMapping(InputQueueNameMapping inputQueueNameMapping) {
    	final Input input = inputQueueNameMapping.getInput();

    	if( input != null ) {
    		this.inputQueueNameMappingPerPhase.get(inputQueueNameMapping.getInput().getSimulationPhase()).add(inputQueueNameMapping);
    	}
    	else {
    		LoggingHelper.error().withBorder().log("No Input available for Queue %s",inputQueueNameMapping.getQueue());
    	}
    	final SimulationPhase phase = input.getSimulationPhase();

    	if( LoggingHelper.isLevelDebugOrTrace() ) {
    		System.out.println("VC::addInputQueueNameMapping: adding mapping for phase " + phase + ", Contents of Mapping list: ");
    		this.inputQueueNameMappingPerPhase.get(phase).forEach(im -> {
    			System.out.println("--> VC: Queue=" + im.getQueue() + ", Input=" + im.getInput().getName() + ", Input-Phase=" + im.getInput().getSimulationPhase());
    		});
    	}
    }

    public void addModelInputInterface(ModelInputInterface modelInputInterface) {
    	this.modelInputInterfaces.put(modelInputInterface.getSimulationPhase(), modelInputInterface);
    }

    public ModelInputInterface getModelInputInterface( SimulationPhase simulationPhase ) {
    	return this.modelInputInterfaces.get(simulationPhase);
    }

    /**
     * get a list of {@link InputQueueNameMappings} for a given {@link SimulationPhase}
     *
     * @param phase the given {@link SimulationPhase}
     * @return a list of found {@link InputQueueNameMappings}s, may be empty
     */
    public List<InputQueueNameMapping> getInputQueueNameMappings(SimulationPhase phase) {
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
     * Save the value(s) received from another block (via RabbitMQ) in temporary buffer, to send them to the wrapper when
     * tact sync is received or when values should be forwarded immediately to the wrapper
     *
     *
     * @param rawValue    Value to save, can be JSON, string or number
     * @param targetInput Target input of this value transmission
     */
    public void processValue(final Object rawValue, final Input targetInput) {
        final String dataType = targetInput.getType().getValue();
        final String inputName = targetInput.getName();
        final SimulationPhase simulationPhase = targetInput.getSimulationPhase();
        LoggingHelper.debug().log("saving Value " + rawValue + " (type=" + dataType + ", Input=" + inputName+ ")");

        ValueHelper.Result result = ValueHelper.getValue(rawValue, dataType);

        final ModelInputInterface modelInputInterface = ModelInputInterface.getModelInputInterface(simulationPhase);
        modelInputInterface.addToValues(inputName, result.value());
		LoggingHelper.debug().log("MII (after value was added): " + modelInputInterface.getValues());

        LoggingHelper.trace().messageObject(this.baseMessage)
        .log("Saved received %s value in Phase=%s for input  %s  value=%s  \t\t#vals=%d", result.typeName(), simulationPhase, inputName, result.value(),
        		modelInputInterface.getValues().size() );

        /**
         * some blocks can provide values but the current block can have a different start point
         */
        if( this.communicationPoint < this.startPoint ) {
			return;
		}

        switch (targetInput.getCommunicationType()) {
			case STEPBASED, STEPBASED_STATIC -> {

				if( SimulationPhase.EXECUTE.equals(simulationPhase)){
					// REFACTOR: add REQUIRED_VALUES?
					switch (this.block.getSyncStrategy()) {
						case INSTANT: {
							// write values immediately to the wrapper
							LoggingHelper.debug().messageObject(this.baseMessage).log("Forwarding value '%s' due to SyncStrategy INSTANT", result.value());
							this.sendValuesToWrapper(simulationPhase, this.communicationPoint);
						}
						case ALL_VALUES: {
							// if all values are given:
							if( modelInputInterface.getValues().size() == this.block.getNumberOfDynamicInputs() ) {
								this.sendValuesToWrapper(simulationPhase, this.communicationPoint);
							}
						}
						case WAIT_FOR_SYNC: {
							// do nothing, wait for SYNC
//							if( modelInputInterface.getValues().keySet().containsAll(requiredInputNames) ) {
//								this.writeValues(modelInputInterface);
//							}
						}
					}
				}
			}
			default -> {
				final var message = StringTemplates.EXPECTED_INPUT_OF_TYPE_BUT_RECEIVED.formatted(CommunicationType.STEPBASED, targetInput.getCommunicationType());
				LoggingHelper.error().messageObject(this.baseMessage).log(message);
				throw new TypeMismatchException(message);
			}
        }
    }

    /**
     *
     * @param modelInputInterface
     */
    public boolean areAllRequiredInputValuesGiven(ModelInputInterface modelInputInterface) {
    	return modelInputInterface.getValues().keySet().containsAll(this.requiredInputNames);
   }



    public void writeValues( ModelInputInterface modelInputInterface) {
    	this.writeValues(modelInputInterface, null);
    }

    public void writeValues( ModelInputInterface modelInputInterface, Integer communicationPoint) {
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

    public void writePreviousValues( ModelInputInterface modelInputInterface) {
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
}
