/*
 * Copyright (c) 2025-2026
 * Karlsruhe Institute of Technology - Institute for Automation and Applied Informatics (IAI)
 */
package edu.kit.iai.webis.proofworker.util;

import java.util.HashMap;
import java.util.Map;

import org.springframework.stereotype.Component;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import edu.kit.iai.webis.proofutils.CommonStringTemplates;
import edu.kit.iai.webis.proofutils.LoggingHelper;
import edu.kit.iai.webis.proofutils.exception.MappingException;
import edu.kit.iai.webis.proofutils.model.SimulationPhase;
import edu.kit.iai.webis.proofutils.wrapper.Block;
import edu.kit.iai.webis.proofworker.config.WorkerConfig;

/**
 * Helper class for mapping outputs to inputs.
 * Note: there is only one block relevant in the worker
 */
@Component
public class MappingHelper {

    private final WorkerConfig workerConfig;
    private final Block block;
    private final Gson gson;

    public MappingHelper(final WorkerConfig workerConfig, final Block block, final Gson gson) {
        this.workerConfig = workerConfig;
        this.block = block;
        this.gson = gson;
   }

    /**
     * get the input mappings as key value pair names of the underlying {@link Block} in JSON format
     *
     * @return the input mappings for all {@link SimulationPhase}s as key value pair names in JSON format
     */
    public String getInputMappingsAsJsonString() {
    	final Map<String, String> allMappingNames = new HashMap<String, String>();
    	for( SimulationPhase sPhase : SimulationPhase.values() ) {
    		if( this.block.getInputNameMappings(sPhase) != null ) {
				allMappingNames.putAll(this.block.getInputNameMappings(sPhase));
			}
    	}
        return new Gson().toJson(allMappingNames.values());
    }

    /**
     * get the output mappings as key value pair names of the underlying {@link Block} in JSON format
     *
     * @return the output mappings for all {@link SimulationPhase}s as key value pair names in JSON format
     */
    public String getOutputMappingsAsJsonString() {
    	final Map<String, String> allMappingNames = new HashMap<String, String>();
    	for( SimulationPhase sPhase : SimulationPhase.values() ) {
    		if( this.block.getOutputNameMappings(sPhase) != null ) {
				allMappingNames.putAll(this.block.getOutputNameMappings(sPhase));
			}
    	}
    	return new Gson().toJson(allMappingNames.values());
    }

    /**
     * Map the input value to a target name
     * @param resultingData the data that will be added to the resulting value message (will be enhanced for each call)
     * @param inputVarValue the value of the source
     * @param inputDataType the data type of the input
     * @param modelVarName the name of the corresponding target
     */
    public void mapInputValue(
    		final JsonObject resultingData,
    		final Object inputVarValue,
    		final String inputDataType,
    		final String modelVarName
    		)
    {
        LoggingHelper.debug().log("---->  Source (JSON): " + inputVarValue + ",  DATA TYPE: " + inputDataType);
        if( inputVarValue != null ) {

        	switch (inputDataType.toLowerCase()) {
        		case CommonStringTemplates.TYPE_JSON_VALUE ->
        			resultingData.add(modelVarName, this.gson.fromJson(inputVarValue.toString(), JsonObject.class));
                case CommonStringTemplates.TYPE_STRING_VALUE,
                     CommonStringTemplates.TYPE_FILE_NAME_VALUE ->
        			resultingData.addProperty(modelVarName, String.valueOf(inputVarValue.toString()));
        		case CommonStringTemplates.TYPE_FLOAT_VALUE ->
        			resultingData.addProperty(modelVarName, Double.valueOf(inputVarValue.toString()));
        		case CommonStringTemplates.TYPE_INTEGER_VALUE ->
        			resultingData.addProperty(modelVarName, Integer.valueOf(inputVarValue.toString()));
        		case CommonStringTemplates.TYPE_INTEGER_ARRAY,
        			CommonStringTemplates.TYPE_FLOAT_ARRAY,
        			CommonStringTemplates.TYPE_STRING_ARRAY ->
        			resultingData.add(modelVarName, this.gson.fromJson(inputVarValue.toString(), JsonArray.class));
                default -> {
        			final String message = StringTemplates.UNEXPECTED_TYPE_VALUE.formatted(inputDataType);
        			LoggingHelper.error().log(message);
        			throw new MappingException(message);
        			}
        	}
        } else {
        	final var message = StringTemplates.COULD_NOT_FIND_CORRESPONDING_IO.formatted(inputVarValue, "");
        	LoggingHelper.error().log(message);
        	throw new MappingException(message);
        }
     }

    /**
     * Map the output value to a
     * @param outputVarValue
     * @param outputType
     * @return the value of the output variable as a string
     * @throws MappingException
     */
    public String mapOutputValue( JsonElement outputVarValue, String outputType) throws MappingException
    {
            LoggingHelper.debug().log("---->  Source (JSON): " + outputVarValue + ",  DATA TYPE: " + outputType);
            if (outputVarValue != null ) {
                switch (outputType) {
                    case CommonStringTemplates.TYPE_JSON_VALUE:
                        if ((outputVarValue).isJsonObject()) {
                        	return outputVarValue.getAsJsonObject().toString();
                        } else if (outputVarValue.isJsonArray()) {
                        	return outputVarValue.getAsJsonArray().toString();
                        } else {
                            throw new MappingException(StringTemplates.COULD_NOT_UNMAP_S_WITH_TYPE.formatted(outputVarValue,
                                    CommonStringTemplates.TYPE_JSON_VALUE));
                        }
                    case CommonStringTemplates.TYPE_STRING_VALUE:
                        if (outputVarValue.isJsonPrimitive() && outputVarValue.getAsJsonPrimitive().isString()) {
                            return outputVarValue.getAsString();
                        } else {
                            throw new MappingException(StringTemplates.COULD_NOT_UNMAP_S_WITH_TYPE.formatted(outputVarValue,
                                    CommonStringTemplates.TYPE_STRING_VALUE));
                        }
                    case CommonStringTemplates.TYPE_INTEGER_ARRAY,
                         CommonStringTemplates.TYPE_FLOAT_ARRAY,
                         CommonStringTemplates.TYPE_STRING_ARRAY:
                        if (outputVarValue.isJsonArray()) {
                            return outputVarValue.getAsJsonArray().toString();
                        } else {
                            throw new MappingException(StringTemplates.COULD_NOT_UNMAP_S_WITH_TYPE.formatted(outputVarValue,
                                "JsonArray"));
                        }
                    case CommonStringTemplates.TYPE_FLOAT_VALUE:
                    	if (outputVarValue.isJsonPrimitive() && outputVarValue.getAsJsonPrimitive().isNumber()) {
                            return String.valueOf(outputVarValue.getAsDouble());
                        } else {
                            throw new MappingException(StringTemplates.COULD_NOT_UNMAP_S_WITH_TYPE.formatted(outputVarValue,
                                    CommonStringTemplates.TYPE_FLOAT_VALUE));
                        }
                    case CommonStringTemplates.TYPE_INTEGER_VALUE:
                        if (outputVarValue.isJsonPrimitive() && outputVarValue.getAsJsonPrimitive().isNumber()) {
                            return String.valueOf(outputVarValue.getAsInt());
                        } else {
                            throw new MappingException(StringTemplates.COULD_NOT_UNMAP_S_WITH_TYPE.formatted(outputVarValue,
                                    CommonStringTemplates.TYPE_INTEGER_VALUE));
                        }
                    default:
                        throw new MappingException(StringTemplates.UNEXPECTED_TYPE_VALUE.formatted(outputType));
                }
            } else {
                throw new MappingException("Output value of mapping source is NULL!_Value is " + outputVarValue + ", Type=" + outputType);
            }
    }

}
