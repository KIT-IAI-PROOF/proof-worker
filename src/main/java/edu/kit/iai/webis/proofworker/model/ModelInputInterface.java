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
     * Map containing the name mappings for the required inputs of a block
     */
    private Map<String, String> requiredInputNameMappings = new HashMap<String, String>();
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
						mii.missingRequiredInputValues.add(input.getName());
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
    public void addToValues(final String name, final Object value) {
		synchronized( this.values ) {
			this.values.put(name, value);
		}
		this.missingRequiredInputValues.remove(name);

        LoggingHelper.debug().log("=> value '%s' saved, #values=%d,  all values given: %s", name, this.values.size(), this.allNeededValuesGiven);

        switch (this.syncStrategy) {
			case ALL_VALUES -> {
				if( this.values.size() == this.inputNameMappings.size() ) {
					this.allNeededValuesGiven = true;
				}
			}
			case WAIT_FOR_SYNC -> {
				if( this.inputNameMappings.size() == 0 ) { // no inputs at all, e.g. a reader
					LoggingHelper.debug().log("no mappings and values for this block");
					this.allNeededValuesGiven = true;
				}
				else if( this.requiredInputNameMappings.size() > 0 ) {
					if( this.missingRequiredInputValues.isEmpty() ) {
						LoggingHelper.debug().log("requiredInputNameMappings.size() > 0 (%d) && all req. values given", this.requiredInputNameMappings.size());
						this.allNeededValuesGiven = true;
					}
					else {
						LoggingHelper.debug().log("requiredInputNameMappings.size() > 0 (%d) but not all req. values given, missing values: %s",
								this.requiredInputNameMappings.size(), this.missingRequiredInputValues);
					}
				}
				else if( this.inputNameMappings.size() > 0
						&& this.values.size() > 0)  // any non-required value given
				{
					LoggingHelper.debug().log("inputNameMappings.size() > 0 (%d) && this.values.size() > 0  (%d)",
							this.inputNameMappings.size(), this.values.size());
					this.allNeededValuesGiven = true;
				}
			}
			case INSTANT -> {
				this.sentValues.put(name, value);
				if( this.sentValues.keySet().containsAll( this.requiredInputNameMappings.keySet() ) ) {
					this.allNeededValuesGiven = true;
				}
			}
        }
        LoggingHelper.debug().log("=> all values given: %s", this.allNeededValuesGiven);
    }

	public void useDefaultValues() {
		this.missingRequiredInputValues.forEach(in -> {
			this.block.getInputs(this.simulationPhase).forEach(input -> {
				if( input.getModelVarName().equals(in) ) {
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
		this.missingRequiredInputValues.addAll(this.requiredInputNameMappings.keySet());
	}

}
