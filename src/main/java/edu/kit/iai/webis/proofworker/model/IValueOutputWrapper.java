/*
 * Copyright (c) 2025-2026
 * Karlsruhe Institute of Technology - Institute for Automation and Applied Informatics (IAI)
 */
package edu.kit.iai.webis.proofworker.model;


import edu.kit.iai.webis.proofutils.model.InterfaceType;

public interface IValueOutputWrapper {
	
    /**
     * get the type ({@link InterfaceType}) 
     *
     * @return the type
     */
	public InterfaceType getInterfaceType();

	/**
	 * add a given key-value-pair to the existing values. 
	 * Already existing values with the same key are overridden.
	 * @param name the name (key)
	 * @param value the value
	 */
    void addToValues(final String name, final Object value);

    /**
     * clear the existing values
     */
    void clearValues();
    
    /**
     * check whether there are values
     */
    boolean hasValues();
}
