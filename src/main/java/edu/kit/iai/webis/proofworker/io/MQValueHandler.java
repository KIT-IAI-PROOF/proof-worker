/*
 * Copyright (c) 2025-2026
 * Karlsruhe Institute of Technology - Institute for Automation and Applied Informatics (IAI)
 */
package edu.kit.iai.webis.proofworker.io;


import edu.kit.iai.webis.proofutils.LoggingHelper;
import edu.kit.iai.webis.proofutils.exception.MessageException;
import edu.kit.iai.webis.proofutils.helper.DateTimeHelper;
import edu.kit.iai.webis.proofutils.helper.DateTimeHelper.EpochTimeScale;
import edu.kit.iai.webis.proofutils.helper.DateTimeHelper.FormatType;
import edu.kit.iai.webis.proofutils.message.ValueMessage;
import edu.kit.iai.webis.proofutils.wrapper.Input;
import edu.kit.iai.webis.proofworker.services.NotifyController;
import edu.kit.iai.webis.proofworker.services.ValueController;
import edu.kit.iai.webis.proofworker.util.StringTemplates;

/**
 * A delegate (handler) for the interpretation of {@link ValueMessage}s coming from the orchestrator
 */
public class MQValueHandler {

    private final ValueController valueController;
    private final Input input;
    private final NotifyController notifyController;

    public MQValueHandler(
    		final ValueController valueController,
    		final NotifyController notifyController,
    		final Input input) {
        super();
        this.valueController = valueController;
        this.notifyController = notifyController;
        this.input = input;
    }

    /**
     * Specialized message handler for {@link ValueMessage}s
     *
     * @param valueMessage the {@link ValueMessage}
     */
    public void handleMessage(final ValueMessage valueMessage) {
        LoggingHelper.debug()
        .log("\n===== B (%s) ====>  VALUE Message received from Block %s (%s) and Input '%s', value=%s, CP=%d    -> TIME=%s\n",
        		valueMessage.getSimulationPhase(), valueMessage.getLocalBlockId(), valueMessage.getGlobalBlockId(), this.input.getName(),
        		valueMessage.getData(), valueMessage.getCommunicationPoint(),
        		DateTimeHelper.doConversion(valueMessage.getTimeInMillis(), FormatType.TIME_HMSMS,
        				EpochTimeScale.MILLISECOND));
        try {
        	this.valueController.processValue(valueMessage, this.input);
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
