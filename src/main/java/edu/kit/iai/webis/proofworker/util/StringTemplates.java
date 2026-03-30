/*
 * Copyright (c) 2025-2026
 * Karlsruhe Institute of Technology - Institute for Automation and Applied Informatics (IAI)
 */
package edu.kit.iai.webis.proofworker.util;

public class StringTemplates {

    public static final String WORKING_DIR_FILE_TEMPLATE = "/file-%s";
    public static final String COULD_NOT_RETRIEVE_AND_SAVE_PROGRAM = "Could not retrieve and save program";
    public static final String PARAMETER_PATH_NOT_PRESENT = "Parameter path not present";
    public static final String COULD_NOT_SETUP_MAIN_IO_PIPE_STREAMS = "Could not setup main IO pipe streams";
    public static final String COULD_NOT_HANDLE_SYNC_MESSAGE = "Could not handle SYNC message (%s)";
    public static final String COULD_NOT_HANDLE_VALUE_MESSAGE = "Could not handle VALUE message (%s)";
    public static final String COULD_NOT_OPEN_FILE_READER = "Could not open file reader";
    public static final String COULD_NOT_OPEN_STREAM_READER = "Could not open stream reader";
    public static final String COULD_NOT_CONVERT_TYPES_CHECK_CONFIGURATION =
            "Could not convert types, check configuration";
    public static final String TYPE_MISMATCH = "Type mismatch between expected type %s and received type %s";
    public static final String EXPECTED_INPUT_OF_TYPE_BUT_RECEIVED = "Expected Input of type %s, but received %s";
    public static final String COULD_NOT_FIND_CORRESPONDING_IO =
            "Could not find corresponding ioSource with name %s in mapping (%s) while trying to map object";
    public static final String UNEXPECTED_TYPE_VALUE = "Unexpected type value %s";
    public static final String VALUE_SPECIFIED_IN_MAPPING_WAS_NOT_FOUND =
            "Value specified in mapping %s to %s was not found, did you send all static input values for init?";
    public static final String COULD_NOT_UNMAP_S_WITH_TYPE = "Could not unmap %s with Type %s";
    public static final String SOURCE_VALUE_OF_MAPPING_SOURCE_IS_NULL =
            "Source value of mapping source %s is null, mapping was %s, data was (%s)";
    public static final String STARTING_BLOCK = "starting Block %d (%s) ...";
    public static final String BLOCK_STARTED = "Block %d (%s) started";
    public static final String BLOCK_RUNNING = "Block %d (%s) is running";
    public static final String BLOCK_NOT_FOUND =
            "Block not found (no JSON config is given and no repository entry exists for block id '%s')";
    public static final String WORKFLOW_NOT_FOUND =
            "Workflow not found (no JSON config is given and no repository entry exists for workflow id '%s')";
    public static final String BLOCK_NOTIFY_SENT_TO_ORCHESTRATOR = "Block notify sent to orchestrator";
    public static final String VALUE_FOR_IOINTERFACE_NOT_PRESENT_IGNORE_AND_PROCEED =
            "Value for IOInterface not present in the current step, ignore missing values and proceed workflow. " +
                    "Block: '%s'";
    public static final String VALUE_FOR_IOINTERFACE_NOT_PRESENT_EXIT_BLOCK =
            "Value for IOInterface not present in the current step, exiting block. Block: '%s'";
    public static final String VALUE_FOR_IOINTERFACE_NOT_PRESENT_RETRY_NEXT_STEP =
            "Value for IOInterface not present in the current step, retry on next step. Block: '%s'";
    public static final String VALUE_FOR_IOINTERFACE_NOT_PRESENT_USING_LATEST_VALUE =
            "Value for IOInterface not present in the current step, using latest value. Block: '%s'";
    public static final String BLOCK_IS_WAITING_FOR_REQUIRED_INPUT = "Block %s is still waiting for required input. Waiting for %d seconds";

}
