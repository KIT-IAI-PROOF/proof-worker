/*
 * Copyright (c) 2025-2026
 * Karlsruhe Institute of Technology - Institute for Automation and Applied Informatics
 */

package edu.kit.iai.webis.proofworker.io;


import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.json.JSONObject;

import com.google.gson.Gson;

import edu.kit.iai.webis.proofutils.Colors;
import edu.kit.iai.webis.proofutils.LoggingHelper;
import edu.kit.iai.webis.proofutils.MessageBuilder;
import edu.kit.iai.webis.proofutils.exception.MessageException;
import edu.kit.iai.webis.proofutils.helper.DateTimeHelper;
import edu.kit.iai.webis.proofutils.helper.DateTimeHelper.EpochTimeScale;
import edu.kit.iai.webis.proofutils.helper.DateTimeHelper.FormatType;
import edu.kit.iai.webis.proofutils.message.MessageType;
import edu.kit.iai.webis.proofutils.message.NotifyMessage;
import edu.kit.iai.webis.proofutils.message.ValueMessage;
import edu.kit.iai.webis.proofutils.model.SimulationPhase;
import edu.kit.iai.webis.proofutils.model.SimulationStatus;
import edu.kit.iai.webis.proofutils.wrapper.Input;
import edu.kit.iai.webis.proofworker.exception.TypeMismatchException;
import edu.kit.iai.webis.proofworker.exception.ValueConfigException;
import edu.kit.iai.webis.proofworker.services.NotifyController;
import edu.kit.iai.webis.proofworker.services.ValueController;
import edu.kit.iai.webis.proofworker.util.StringTemplates;

/**
 * A delegate (handler) for the interpretation of {@link ValueMessage}s coming from the orchestrator
 */
public class MQInitValueHandler {

    private final ValueController valueController;
    private final List<Input> staticInputs;
    private final NotifyController notifyController;

    public MQInitValueHandler(
    		final ValueController valueController,
    		final NotifyController notifyController,
    		final List<Input> staticInputs) {
        super();
        this.valueController = valueController;
        this.notifyController = notifyController;
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

        if( SimulationPhase.INIT == valueMessage.getSimulationPhase()) {
        	LoggingHelper.debug()
        	.log("\n===== B (INIT) ====>  VALUE Message received for INIT phase, value=%s    -> TIME=%s\n",
        			valueMessage.getData(),
        			DateTimeHelper.doConversion(valueMessage.getTimeInMillis(), FormatType.TIME_HMSMS,
        					EpochTimeScale.MILLISECOND));
        	try {
        		JSONObject jsonObject = new JSONObject(new Gson().toJson(value));

        		final Map<Input, Object> staticInputValues = new HashMap<Input, Object>();

        		for (final Input staticInput : this.staticInputs) {
        			if( jsonObject.has(staticInput.getName()) ) {
        		    	try {
//        		    		this.valueController.processInitValue(jsonObject.get(staticInput.getName()), staticInput);
        		    		staticInputValues.put(staticInput, jsonObject.get(staticInput.getName()));
        				} catch (TypeMismatchException | ValueConfigException e) {
        		    		LoggingHelper.error().log("ERROR getting value for input '%s'. Reason: %s", staticInput.getName(), e.getMessage() );
        		    		NotifyMessage notifyMessage = (NotifyMessage)MessageBuilder.init(MessageType.NOTIFY)
        		    				.copyOf(valueMessage)
        		    				.blockStatus(SimulationStatus.ERROR_INIT)
        		    				.errorText("ERROR getting value for input '%s'. Reason: %s".formatted(staticInput.getName(), e.getMessage()) )
        		    				.build();
        		    		this.notifyController.sendNotifyMessage(notifyMessage);
        				}
        			}
        			else if( staticInput.isRequired() ){
        				LoggingHelper.warn().log("Input '%s' is required and not given. Taking default value!", staticInput.getName() );
        				if( staticInput.getDefaultValue() != null ) {
        		    		staticInputValues.put(staticInput, staticInput.getDefaultValue());
        				}
        				else {
        					LoggingHelper.error().messageColor(Colors.ANSI_RED).log("Input '%s' is required and not given and default value is not available!", staticInput.getName() );
        					NotifyMessage notifyMessage = (NotifyMessage)MessageBuilder.init(MessageType.NOTIFY)
        							.copyOf(valueMessage)
        							.blockStatus(SimulationStatus.ERROR_INIT)
        							.errorText("Input '" + staticInput.getName() + "' is required and not given and default value is not available!")
        							.build();
        					this.notifyController.sendNotifyMessage(notifyMessage);
        				}
        			}
        			else {
        				LoggingHelper.debug().messageColor(Colors.ANSI_BLUE).log("Input '%s' is not required and not given => not saved, Wrapper must set default value for it",
        						staticInput.getName() );
        			}
        		}
        		this.valueController.processInitValues(staticInputValues);
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
    }
}
