/*
 * Copyright (c) 2025-2026
 * Karlsruhe Institute of Technology - Institute for Automation and Applied Informatics (IAI)
 */
package edu.kit.iai.webis.proofworker.io;


import java.util.List;
import java.util.Objects;

import org.json.JSONObject;

import com.google.gson.Gson;

import edu.kit.iai.webis.proofutils.Colors;
import edu.kit.iai.webis.proofutils.LoggingHelper;
import edu.kit.iai.webis.proofutils.exception.MessageException;
import edu.kit.iai.webis.proofutils.helper.DateTimeHelper;
import edu.kit.iai.webis.proofutils.helper.DateTimeHelper.EpochTimeScale;
import edu.kit.iai.webis.proofutils.helper.DateTimeHelper.FormatType;
import edu.kit.iai.webis.proofutils.message.ValueMessage;
import edu.kit.iai.webis.proofutils.model.CommunicationType;
import edu.kit.iai.webis.proofutils.wrapper.Input;
import edu.kit.iai.webis.proofworker.services.ValueController;
import edu.kit.iai.webis.proofworker.util.StringTemplates;

/**
 * A delegate (handler) for the interpretation of {@link ValueMessage}s coming from the orchestrator
 */
public class MQValueHandler {

    private final ValueController valueController;
    private final Input input;
    private final List<Input> staticInputs;


    public MQValueHandler(ValueController controller, Input input, List<Input> staticInputs) {
        super();
        this.valueController = controller;
        this.input = input;
        this.staticInputs = staticInputs;
    }

    /**
     * Specialized message handler for {@link ValueMessage}s
     *
     * @param valueMessage the {@link ValueMessage}
     */
    public void handleMessage(final ValueMessage valueMessage) {
        final var value = valueMessage.getData();
        Objects.requireNonNull(value, "value is not given!");

        switch (valueMessage.getSimulationPhase()) {

	        case INIT -> {// Block is not yet initialized => static inputs

	        	LoggingHelper.debug()
	        	.log("\n===== B (INIT) ====>  VALUE Message received for INIT phase for Inputs '%s', value=%s    -> TIME=%s\n",
	        			this.staticInputs.toString(),
	        			valueMessage.getData(),
	        			DateTimeHelper.doConversion(valueMessage.getTimeInMillis(), FormatType.TIME_HMSMS,
	        					EpochTimeScale.MILLISECOND));
	        	try {
	        		JSONObject jsonObject = new JSONObject(new Gson().toJson(value));

	        		for (final Input staticInput : this.staticInputs) {
	        			if( jsonObject.has(staticInput.getName()) ) {
	        				this.valueController.processValue(jsonObject.get(staticInput.getName()), staticInput);
	        			}
	        			else if( staticInput.isRequired() ){
	        				LoggingHelper.error().messageColor(Colors.ANSI_RED).log("Input '%s' is required and not given!", staticInput.getName() );
	        			}
	        			else {
	        				LoggingHelper.debug().messageColor(Colors.ANSI_BLUE).log("Input '%s' is not required and not given => not saved, Wrapper must set default value for it",
	        						staticInput.getName() );
	        			}
	        		}
	        	} catch (final Exception e) {
	        		String messageText =
	        				"(time=" + valueMessage.getTime() + "('" + DateTimeHelper.doConversion(valueMessage.getTime(),
	        						DateTimeHelper.FormatType.ISO8601) + "'), phase=" + valueMessage.getSimulationPhase() + ")";
	        		final String error =
	        				this.getClass().getSimpleName() + ":: " + StringTemplates.COULD_NOT_HANDLE_VALUE_MESSAGE.formatted(messageText);
	        		LoggingHelper.error().log(error);
	        		throw new MessageException(error, e);
	        	}
			}
			default -> {// Block is already initialized

				LoggingHelper.debug()
				.log("\n===== B (%s) ====>  VALUE Message received from Block %s (%s) and Input '%s', value=%s    -> TIME=%s\n",
						valueMessage.getSimulationPhase(), valueMessage.getLocalBlockId(), valueMessage.getGlobalBlockId(), this.input.getName(),
						valueMessage.getData(),
						DateTimeHelper.doConversion(valueMessage.getTimeInMillis(), FormatType.TIME_HMSMS,
								EpochTimeScale.MILLISECOND));
				try {
					if (this.input.getCommunicationType().equals(CommunicationType.STEPBASED)) {
						this.valueController.processValue(value, this.input);
					}
				} catch (final Exception e) {
					String messageText =
							"(time=" + valueMessage.getTime() + "('" + DateTimeHelper.doConversion(valueMessage.getTime(),
									DateTimeHelper.FormatType.ISO8601) + "'), phase=" + valueMessage.getSimulationPhase() + ")";
					final String error =
							this.getClass().getSimpleName() + ":: " + StringTemplates.COULD_NOT_HANDLE_VALUE_MESSAGE.formatted(messageText);
					LoggingHelper.error().log(error);
					throw new MessageException(error, e);
				}
			}
        }// switch
    }
}
