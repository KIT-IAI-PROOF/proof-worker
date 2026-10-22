/*
 * Copyright (c) 2025-2026
 * Karlsruhe Institute of Technology - Institute for Automation and Applied Informatics (IAI)
 */
package edu.kit.iai.webis.proofworker.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import edu.kit.iai.webis.proofutils.LoggingHelper;
import edu.kit.iai.webis.proofutils.model.InterfaceType;
import edu.kit.iai.webis.proofutils.model.SimulationPhase;
import edu.kit.iai.webis.proofutils.model.SyncStrategy;
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
     * Map containing the name mappings for the required inputs of a block: <name, modelVarName>
     */
    private Map<String, String> requiredInputNameMappings = new HashMap<String, String>();

	/**
	 * Collection of elements where every element is unique: <modelVarName>
	 */
    private HashSet<String> missingRequiredInputValues = new HashSet<String>();

    /**
     * The writer instance to transfer data to the model (wrapper)
     */
    private IWriter writer;

    /**
     * the values coming from the orchestrator (phase INIT) or from other blocks (phase EXECUTE)
     */
//    private Map<String, Object> values = Collections.synchronizedMap(new HashMap<String, Object>());
    private ConcurrentHashMap<String, Object> values = new ConcurrentHashMap<String, Object>();

    /**
     * the values are sent instantly and deleted or are store until cleared. For this,
     * another Map is used to collect the values already sent
     */
    private ConcurrentHashMap<String, Object> sentValues = new ConcurrentHashMap<String, Object>();

    /**
     * the values stored after the last SYNC
     */
    private Map<String, Object> previousValues = Collections.synchronizedMap(new HashMap<String, Object>());

    /**
     * The {@link InterfaceType} for the data communication between worker and wrapper.
     * Values may be FILE, STDIO, or SOCKET
     */
    private InterfaceType interfaceType;

    private SyncStrategy syncStrategy;

    private final static Map<SimulationPhase, ModelInputInterface> modelInputInterfacesPerPhase = new HashMap<SimulationPhase, ModelInputInterface>(5);

    private final static EnumMap<SimulationPhase, Map<String, String>> inputMappingNamesPerPhase = new EnumMap<SimulationPhase, Map<String, String>>(
    		Map.of(
    				SimulationPhase.INIT, new HashMap<String, String>(),
    				SimulationPhase.EXECUTE, new HashMap<String, String>(),
    				SimulationPhase.FINALIZE, new HashMap<String, String>()
    				));

	private boolean allNeededValuesGiven = false;

	private ModelInputInterface() {}

	private Block block;

    public static ModelInputInterface createModelInputInterface( SimulationPhase simulationPhase, Block block) {
    	ModelInputInterface mii = new ModelInputInterface();
    	mii.block = block;
    	mii.simulationPhase = simulationPhase;
    	mii.inputNameMappings = block.getInputNameMappings(simulationPhase);
    	mii.interfaceType = block.getInterfaceType();
    	mii.syncStrategy = mii.simulationPhase == SimulationPhase.INIT ? SyncStrategy.WAIT_FOR_SYNC : block.getSyncStrategy();
    	mii.requiredInputNameMappings = new HashMap<String, String>(block.getRequiredDynamicInputs().size());

    	final List<String> ris = new ArrayList<String>();
    	block.getRequiredDynamicInputs().forEach( input -> {
    		if( input.getSimulationPhase() == simulationPhase ) {
				mii.requiredInputNameMappings.put(input.getName(),input.getModelVarName());
				ris.add(input.getName());
				// add start values for required inputs
				if( simulationPhase == SimulationPhase.EXECUTE ) {
					if( input.getStartValue() != null ) {
				    	LoggingHelper.debug().log("setting start value for required input %s for phase %s:  %s",
				    			input.getName(), simulationPhase, input.getStartValue());
						mii.addToValues(input.getModelVarName(), input.getStartValue());
					}
					else {
						// If no start value given, add to missing required input.
						mii.missingRequiredInputValues.add(input.getModelVarName());
				    	LoggingHelper.warn().log("no start value given for required input %s for phase %s!",
				    			input.getName(), simulationPhase);
					}
				}
			}
    	});
    	LoggingHelper.debug().log("Required inputs for phase %s:  %s", simulationPhase, ris);

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

    public synchronized Map<String, Object> getValues() {
        return this.values;
    }

    public synchronized Map<String, Object> getSentValues() {
    	return this.sentValues;
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
    public void addToValues(final String modelVarName, final Object value) {
		synchronized( this.values ) {
			this.values.put(modelVarName, value);
		}
		// @TODO:  Reduktion funzt nur für den ersten Step der Phase => "reset" nach dem Senden der Values.
		// Beachten: was passiert bei (required) Values, die nach dem Senden und vor der NotifyMessage "Ex_STEP_Finished" des Wrappers ankommen?
		this.missingRequiredInputValues.remove(modelVarName);

        LoggingHelper.debug().log("=> value '%s' saved, #values=%d,  all values given: %s", modelVarName, this.values.size(), this.allNeededValuesGiven);

        if( this.syncStrategy == SyncStrategy.INSTANT ) {
        	this.sentValues.put(modelVarName, value);
        }
        this.updateAllNeededValuesGivenState();
        LoggingHelper.debug().log("=> all values given: %s", this.allNeededValuesGiven);
    }

	/**
	 * (Re-)evaluate whether all needed values are given for the current syncStrategy.
	 * Must not depend on a value having just arrived: optional (non-required) inputs may
	 * never receive a value at all, so readiness has to be derivable from the current
	 * state alone. Called lazily from allNeededValuesGiven().
	 */
	private void updateAllNeededValuesGivenState() {
		LoggingHelper.debug().log("Update allValuesGiven for strategy %s", this.syncStrategy);
		switch (this.syncStrategy) {
			case ALL_VALUES -> {
				LoggingHelper.debug().log("Strategy '%s': allValuesGiven= (%d==%d)", this.syncStrategy, this.values.size(), this.inputNameMappings.size());
				if( this.values.size() == this.inputNameMappings.size() ) {
					this.allNeededValuesGiven = true;
				}
			}
			case WAIT_FOR_SYNC -> {
				if( this.inputNameMappings.size() == 0 ) { // no inputs at all, e.g. a reader
					LoggingHelper.debug().log("no mappings and values for this block");
					this.allNeededValuesGiven = true;
				}
				else if( this.missingRequiredInputValues.isEmpty() ) {
					// all required inputs (if any) have values; remaining (optional) inputs may still be missing, that's fine
					LoggingHelper.debug().log("all required inputs (%d) have values, remaining inputs (if any) are optional", this.requiredInputNameMappings.size());
					this.allNeededValuesGiven = true;
				}
				else {
					LoggingHelper.debug().log("requiredInputNameMappings.size() > 0 (%d) but not all req. values given, missing values: %s",
							this.requiredInputNameMappings.size(), this.missingRequiredInputValues);
				}
			}
			case INSTANT -> {
				LoggingHelper.debug().log("Strategy '%s': allValuesGiven= allkeysContaind?:\n%s\n%s", this.syncStrategy, this.sentValues.keySet(), this.requiredInputNameMappings.keySet());
				if( this.sentValues.keySet().containsAll( this.requiredInputNameMappings.keySet() ) ) {
					this.allNeededValuesGiven = true;
				}
			}
        }
		LoggingHelper.debug().log("Set allValuesGiven to '%b'", this.allNeededValuesGiven);
	}

	public void useDefaultValues() {
		this.missingRequiredInputValues.forEach(modelVarName -> {
			this.block.getInputs(this.simulationPhase).forEach(input -> {
				if( input.getModelVarName().equals(modelVarName) ) {
					if( input.getDefaultValue() != null ) {
						this.addToValues(input.getModelVarName(), input.getDefaultValue() );
					}
					else {
						LoggingHelper.debug().log("No default value given for required input %s (value still missing)", input.getName());
					}
				}
			});
		});
	}

	public boolean allNeededValuesGiven() {
		if( !this.allNeededValuesGiven ) {
			this.updateAllNeededValuesGivenState();
		}
		return this.allNeededValuesGiven;
	}

	public void resetValueCounter() {
		this.allNeededValuesGiven = false;
	}

	public void addToSentValues(final String name, final Object value) {
		synchronized( this.sentValues ) {
			this.sentValues.put(name, value);
		}
	}

//    public void clearSentValues() {
//        this.sentValues.clear();
//    }


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

	public boolean areAllValuesSent() {
		return this.inputNameMappings.size() == this.sentValues.size();
	}

	public List<String> getMissingRequiredInputValues() {
		return new ArrayList<String>( this.missingRequiredInputValues );
	}

	/**
	 * prepare the next step. Reset values, save sent values, etc.
	 */
	public void prepareNextStep() {
		this.clearValues();
		this.sentValues.clear();
		this.missingRequiredInputValues.addAll(this.requiredInputNameMappings.values());
	}

}
