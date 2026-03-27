/*
 * Copyright (c) 2025-2026
 * Karlsruhe Institute of Technology - Institute for Automation and Applied Informatics (IAI)
 */
package edu.kit.iai.webis.proofworker.util;

import org.springframework.stereotype.Component;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import com.google.gson.JsonSyntaxException;

import edu.kit.iai.webis.proofutils.CommonStringTemplates;
import edu.kit.iai.webis.proofutils.LoggingHelper;
import edu.kit.iai.webis.proofutils.message.NotifyMessage;
import edu.kit.iai.webis.proofutils.message.ValueMessage;
import edu.kit.iai.webis.proofutils.model.SimulationStatus;
import edu.kit.iai.webis.proofutils.model.SimulationPhase;
import edu.kit.iai.webis.proofworker.services.NotifyController;
import edu.kit.iai.webis.proofworker.services.ValueController;


/**
 * a helper class for the reading of a  {@link ValueMessage} or a {@link NotifyMessage} provided in JSON format
 */
@Component
public class ReaderHelper {

    private final Gson gson;
    private final ValueController valueController;
    private final NotifyController notifyController;

    public ReaderHelper(final Gson gson,
                        final ValueController valueController,
                        final NotifyController notifyController)
    {
    	this.valueController = valueController;
        this.notifyController = notifyController;
        this.gson = gson;
    }

    /**
     * read the string object coming from the wrapper
     * @param line the line read from console
     * @return the {@link SimulationStatus}, used for shutdown handling when a {@link NotifyMessage} is read , or <b>null</b>, when a
     * {@link ValueMessage} is read
     */
    public synchronized void readStringObject(final String line) {

    	try {
    		LoggingHelper.trace().log("LINE=>>%s<<", line);
    		JsonElement elem = JsonParser.parseString(line);
    		if ( elem instanceof JsonObject object ) {
    			final SimulationPhase simulationPhase = SimulationPhase.valueOf(object.get("phase").getAsString());

    			if( object.get("type") instanceof JsonPrimitive typePrim ) {
    				String type = typePrim.getAsString();
    				if( type.equals("NOTIFY") ) {
    					NotifyMessage notifyMessage = this.gson.fromJson(line, NotifyMessage.class);
    					LoggingHelper.debug().log("\n ----- W --------> NOTIFY message received from Wrapper!   (Status = %s) (CP=%d) ... \n",
    							object.get("status"), notifyMessage.getCommunicationPoint());
    					notifyMessage.setSimulationPhase(simulationPhase);
    					notifyMessage.setBlockStatus(SimulationStatus.valueOf(object.get("status").getAsString()));
    					LoggingHelper.trace().log("NotifyMessage: %s\n", notifyMessage );
    					this.notifyController.processNotifyMessage(notifyMessage);
    				}
    				else if( type.equals("VALUE") ) {
    					ValueMessage valueMessage = this.gson.fromJson(line, ValueMessage.class);
    					Object data = this.gson.fromJson(object.get(CommonStringTemplates.DATA_KEY), JsonObject.class);
    					LoggingHelper.debug().log("\n ----- W --------> VALUE message received from Wrapper!  (Phase= %s, CP=%d, data=%s)",
    							simulationPhase, valueMessage.getCommunicationPoint(), (data != null ? data : "no data given!"));
    					valueMessage.setSimulationPhase(simulationPhase);
    					valueMessage.setData(data);
    					this.valueController.processValueMessageFromWrapper(valueMessage);
    				}
    			}
    		} else {
    			LoggingHelper.error().log("expected JSON object, was: '%s'", line);
    		}
    	} catch (JsonSyntaxException e) {
    		LoggingHelper.error().log("wrong JSON syntax: '%s'", line);
    		return;
    	}
    }

}
