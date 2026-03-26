/*
 * Copyright (c) 2025-2026
 * Karlsruhe Institute of Technology - Institute for Automation and Applied Informatics (IAI)
 */
package edu.kit.iai.webis.proofworker.model;

import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;

import edu.kit.iai.webis.proofutils.LoggingHelper;
import edu.kit.iai.webis.proofutils.model.InterfaceType;
import edu.kit.iai.webis.proofutils.model.SimulationPhase;
import edu.kit.iai.webis.proofutils.wrapper.Block;
import edu.kit.iai.webis.proofworker.services.IWriter;

/**
 * contains attributes of (former) class StepBasedOutputWrapper AND
 * of class BlockIODefinitions
 *
 * a ModelInputInterface holds all values needed for all inputs of a block, e.g. all input name mappings and the interface type.
 */
public class ModelInputInterface implements IValueOutputWrapper{

    /**
     * The {@link SimulationPhase} where the step is processed, possible values are INIT, STEP, FINALIZE, or SHUTDOWN
     */
    private SimulationPhase simulationPhase;

    /**
     * Map containing the name mappings for the inputs of a block
     */
    private Map<String, String> inputNameMappings = new HashMap<String, String>();

    /**
     * The writer instance to transfer data to the model (wrapper)
     */
    private IWriter writer;

    /**
     * the values coming from the orchestrator (phase INIT) or from other blocks (phase EXECUTE)
     */
    private Map<String, Object> values = Collections.synchronizedMap(new HashMap<String, Object>());

    /**
     * the values stored after the last SYNC
     */
    private Map<String, Object> previousValues = Collections.synchronizedMap(new HashMap<String, Object>());

    /**
     * The {@link InterfaceType} for the data communication between worker and wrapper.
     * Values may be FILE, STDIO, or SOCKET
     */
    private InterfaceType interfaceType;

    private final static Map<SimulationPhase, ModelInputInterface> modelInputInterfacesPerPhase = new HashMap<SimulationPhase, ModelInputInterface>(5);

    private final static EnumMap<SimulationPhase, Map<String, String>> inputMappingNamesPerPhase = new EnumMap<SimulationPhase, Map<String, String>>(
    		Map.of(
    				SimulationPhase.INIT, new HashMap<String, String>(),
    				SimulationPhase.EXECUTE, new HashMap<String, String>(),
    				SimulationPhase.FINALIZE, new HashMap<String, String>()
    				));

    private ModelInputInterface() {}

    public static ModelInputInterface createModelInputInterface( SimulationPhase simulationPhase, Block block) {
    	ModelInputInterface mii = new ModelInputInterface();
    	mii.simulationPhase = simulationPhase;
    	mii.inputNameMappings = block.getInputNameMappings(simulationPhase);
    	mii.interfaceType = block.getInterfaceType();

    	Map<String, String> imns = inputMappingNamesPerPhase.get(mii.simulationPhase);
    	if( imns != null ) {
    		imns.putAll(mii.inputNameMappings);
    	}
    	modelInputInterfacesPerPhase.put( simulationPhase, mii );

    	return mii;
    }

    public static void setAllWriters(IWriter writer) {
    	for( SimulationPhase sPhase : SimulationPhase.values() ) {
    		ModelInputInterface mii = ModelInputInterface.getModelInputInterface(sPhase);
    		if( mii != null ) {
				mii.setWriter(writer);
			}
    	}
    }

    public SimulationPhase getSimulationPhase() {
    	return this.simulationPhase;
    }

    public Map<String, Object> getValues() {
        return this.values;
    }

    public Map<String, Object> getPreviousValues() {
        return this.previousValues;
    }


    public void setInterfaceType(InterfaceType interfaceType) {
    	this.interfaceType = interfaceType;
    }

    public void setWriter(IWriter writer) {
    	this.writer = writer;
    }

    public IWriter getWriter() {
    	return this.writer;
    }

	public Map<String, String> getInputNameMappings() {
		return this.inputNameMappings;
	}

	public static ModelInputInterface getModelInputInterface( SimulationPhase phase ){
		return modelInputInterfacesPerPhase.get(phase);
	}

	@Override
    public void addToValues(final String name, final Object value) {
        this.values.put(name, value);
        LoggingHelper.trace().log("value '" + name + "' saved, #values=" + this.values.size());
    }

    @Override
    public void clearValues() {
        // Copy contents
        this.previousValues.putAll(this.values);
        this.values.clear();
    }

	@Override
	public InterfaceType getInterfaceType() {
		return this.interfaceType;
	}

	@Override
	public boolean hasValues() {
		return this.values.isEmpty();
	}

}
