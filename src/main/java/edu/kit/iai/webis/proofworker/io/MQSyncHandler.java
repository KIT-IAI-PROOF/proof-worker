/*
 * Copyright (c) 2025-2026
 * Karlsruhe Institute of Technology - Institute for Automation and Applied Informatics (IAI)
 */
package edu.kit.iai.webis.proofworker.io;

import edu.kit.iai.webis.proofutils.Colors;
import edu.kit.iai.webis.proofutils.LoggingHelper;
import edu.kit.iai.webis.proofutils.MessageBuilder;
import edu.kit.iai.webis.proofutils.helper.DateTimeHelper;
import edu.kit.iai.webis.proofutils.helper.DateTimeHelper.EpochTimeScale;
import edu.kit.iai.webis.proofutils.helper.DateTimeHelper.FormatType;
import edu.kit.iai.webis.proofutils.message.MessageType;
import edu.kit.iai.webis.proofutils.message.NotifyMessage;
import edu.kit.iai.webis.proofutils.message.SyncMessage;
import edu.kit.iai.webis.proofutils.model.SimulationStatus;
import edu.kit.iai.webis.proofutils.model.SimulationPhase;
import edu.kit.iai.webis.proofworker.services.NotifyController;
import edu.kit.iai.webis.proofworker.services.SyncController;
import edu.kit.iai.webis.proofworker.util.StringTemplates;

/**
 * A delegate (handler) for the interpretation of {@link SyncMessage}s coming from the orchestrator
 */
public class MQSyncHandler {

    private final SyncController syncController;
    private final NotifyController notifyController;
	private boolean shutdownReceived = false;

    /**
     * Create a MQSyncHandler instance
     *
     * @param syncController the {@link SyncController}
     */
    public MQSyncHandler(SyncController syncController, NotifyController notifyController) {
        super();
        this.syncController = syncController;
        this.notifyController = notifyController;
    }

    /**
     * Specialized message handler for {@link SyncMessage}s
     *
     * @param syncMessage Message as TactMessage
     */
    public void handleMessage(final SyncMessage syncMessage) {
    	LoggingHelper.info().log("===== O ====>  SYNC Message received from Orchestrator!  Phase: %s, Time=%s  \t\t  ---> CP=%d", syncMessage.getSimulationPhase(),
    			DateTimeHelper.doConversion( syncMessage.getTimeInMillis(), FormatType.TIME_HMSMS, EpochTimeScale.MILLISECOND),
    			syncMessage.getCommunicationPoint());

    	if( syncMessage.getSimulationPhase() == SimulationPhase.SHUTDOWN ) {
            LoggingHelper.debug().log("SHUTDOWN RECEIVED FOR THE FIRST TIME!   shutdownReceived=" + this.shutdownReceived);
    		if( ! this.shutdownReceived ) {
    			this.shutdownReceived  = true;
    		}
    		else {
    			LoggingHelper.info().messageColor(Colors.ANSI_PURPLE)
    			.log("ignoring  a further SHUTDOWN message, returning ... ");
    			return;
    		}
    	}

        try {
            this.syncController.processStep(syncMessage);

        } catch (final Exception e) {
            String messageText =
                    "(time=" + syncMessage.getTime() + "('" + DateTimeHelper.doConversion(syncMessage.getTimeInMillis(),
                            DateTimeHelper.FormatType.ISO8601) + "'), phase=" + syncMessage.getSimulationPhase() + ", " +
                            "commPoint=" + syncMessage.getCommunicationPoint() + ", commStepSize="
                            + syncMessage.getCommunicationStepSize() + ")";
            final String error =
                    this.getClass().getSimpleName() + ":: "
                            + StringTemplates.COULD_NOT_HANDLE_SYNC_MESSAGE.formatted(messageText);
            LoggingHelper.error().exception(e).printStackTrace(e).log(error);

            final var notifyMessage = MessageBuilder.init(MessageType.NOTIFY)
            		.copyOf(syncMessage)
            		.simulationPhase(syncMessage.getSimulationPhase())
                    .communicationPoint(syncMessage.getCommunicationPoint())
                    .errorText(error)
                    .blockStatus( syncMessage.getSimulationPhase() == SimulationPhase.INIT
                    		? SimulationStatus.ERROR_INIT
                    		: (syncMessage.getSimulationPhase() == SimulationPhase.EXECUTE ? SimulationStatus.ERROR_STEP : SimulationStatus.ERROR_FINALIZE))
            		.build();
            this.notifyController.sendNotifyMessage((NotifyMessage) notifyMessage);
        }
    }
}

