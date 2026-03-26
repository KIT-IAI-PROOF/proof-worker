/*
 * Copyright (c) 2025-2026
 * Karlsruhe Institute of Technology - Institute for Automation and Applied Informatics (IAI)
 */
package edu.kit.iai.webis.proofworker.model;

import java.util.HashMap;
import java.util.Map;

//import edu.kit.iai.webis.proofmodels.EventLifecycle;
import edu.kit.iai.webis.proofutils.model.InterfaceType;
import edu.kit.iai.webis.proofworker.services.InputFileWriter;
import edu.kit.iai.webis.proofworker.services.InputStreamWriter;

/**
 * Full refactor finished at 25.03.2022
 */
public class EventBasedValueOutputWrapper implements IValueOutputWrapper {

    private InputFileWriter inputFileWriter;
    private InputStreamWriter inputStreamWriter;
    private Map<String, Object> values = new HashMap<>();
    private Map<String, Object> previousValues = new HashMap<>();
    private InterfaceType interfaceType; 

    @Override
    public void addToValues(final String name, final Object value) {
        this.values.put(name, value);
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
