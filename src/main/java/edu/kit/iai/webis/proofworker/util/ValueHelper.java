/*
 * Copyright (c) 2025-2026
 * Karlsruhe Institute of Technology - Institute for Automation and Applied Informatics (IAI)
 */
package edu.kit.iai.webis.proofworker.util;

import java.util.LinkedHashMap;

import org.springframework.stereotype.Component;

import edu.kit.iai.webis.proofutils.Colors;
import edu.kit.iai.webis.proofutils.CommonStringTemplates;
import edu.kit.iai.webis.proofutils.LoggingHelper;
import edu.kit.iai.webis.proofworker.exception.TypeMismatchException;

/**
 * Helper class for mapping outputs to inputs
 */
@Component
public class ValueHelper {

	public final static String LBL_UNKNOWN = "UNKNOWN";
	public final static String LBL_NUMBER = "NUMBER";
	public final static String LBL_STRING = "STRING";
	public final static String LBL_DOUBLE = "DOUBLE";
	public final static String LBL_INTEGER = "INTEGER";
	public final static String LBL_JSON = "JSON";
	public final static String LBL_JSON_ARRAY = "JSON-ARRAY";

    private ValueHelper() {};

    public record Result( Object value, String typeName ) {}

    public static Result getValue(final Object rawValue, final String dataType) throws TypeMismatchException{

    	LoggingHelper.trace().messageColor(Colors.ANSI_RED).log("DataType: " + dataType + ",  raw value: " + rawValue);;
        switch (dataType.toLowerCase()) {
            case CommonStringTemplates.STRING, CommonStringTemplates.TYPE_FILE_NAME_VALUE -> {
                if (rawValue instanceof String value) {
                	return new Result(value, LBL_STRING);
                } else {
                	throw new TypeMismatchException(StringTemplates.TYPE_MISMATCH.formatted(String.class, rawValue.getClass()));
                }
            }
            case CommonStringTemplates.NUMBER, CommonStringTemplates.TYPE_FLOAT_VALUE,
                    CommonStringTemplates.TYPE_INTEGER_VALUE -> {
                if (rawValue instanceof Double || rawValue instanceof Integer || rawValue instanceof Float) {
                    return new Result(rawValue, LBL_NUMBER );
                }
                else if (rawValue instanceof String) {
                    try {
                        if (((String) rawValue).indexOf('.') >= 0) {
                        	return new Result(Double.parseDouble((String) rawValue), LBL_DOUBLE);
                        } else {
                        	return new Result(Integer.parseInt((String) rawValue), LBL_INTEGER);
                        }

                    } catch (Exception e) {
                    	throw new TypeMismatchException(StringTemplates.COULD_NOT_CONVERT_TYPES_CHECK_CONFIGURATION + ", " +
                                "rawValue: " + rawValue + ",  Exception-Msg: " + e.getMessage());
                    }
                }
                else {
                	throw new TypeMismatchException(StringTemplates.TYPE_MISMATCH.formatted(Number.class, rawValue.getClass()));
                }
            }
            case CommonStringTemplates.JSON, CommonStringTemplates.TYPE_OBJECT_VALUE -> {
                if (rawValue instanceof LinkedHashMap value) {
                	return new Result(value, LBL_JSON);
                }
                else if (rawValue instanceof String value) {
                	return new Result(value, LBL_STRING);
                }
                else {
                	throw new TypeMismatchException(StringTemplates.TYPE_MISMATCH.formatted(LinkedHashMap.class,
                            rawValue.getClass()));
                }
            }
            case CommonStringTemplates.TYPE_FLOAT_ARRAY, CommonStringTemplates.TYPE_INTEGER_ARRAY, CommonStringTemplates.TYPE_STRING_ARRAY, CommonStringTemplates.TYPE_OBJECT_ARRAY -> {
            	if (rawValue instanceof LinkedHashMap value) {
                	return new Result(value, LBL_JSON_ARRAY);
            	}
            	else if (rawValue instanceof String value) {
            		return new Result(value, LBL_STRING);
            	}
            	else {
            		throw new TypeMismatchException(StringTemplates.TYPE_MISMATCH.formatted(LinkedHashMap.class,
            				rawValue.getClass()));
            	}
            }
        }
        return new Result(null, LBL_UNKNOWN);
    }

}
