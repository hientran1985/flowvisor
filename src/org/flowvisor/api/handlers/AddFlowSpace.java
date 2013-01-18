package org.flowvisor.api.handlers;

import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.flowvisor.api.APIUserCred;
import org.flowvisor.config.ConfigError;
import org.flowvisor.config.FVConfig;
import org.flowvisor.config.FlowSpace;
import org.flowvisor.config.FlowSpaceImpl;
import org.flowvisor.exceptions.MissingRequiredField;
import org.flowvisor.flows.FlowEntry;
import org.flowvisor.flows.FlowMap;
import org.flowvisor.flows.FlowSpaceUtil;
import org.flowvisor.flows.SliceAction;
import org.flowvisor.log.FVLog;
import org.flowvisor.log.LogLevel;
import org.flowvisor.openflow.protocol.FVMatch;
import org.openflow.protocol.OFMatch;
import org.openflow.protocol.action.OFAction;

import com.thetransactioncompany.jsonrpc2.JSONRPC2Error;
import com.thetransactioncompany.jsonrpc2.JSONRPC2ParamsType;
import com.thetransactioncompany.jsonrpc2.JSONRPC2Response;

public class AddFlowSpace implements ApiHandler<List<Map<String, Object>>> {

	
	
	@Override
	public JSONRPC2Response process(List<Map<String, Object>> params) {
		JSONRPC2Response resp = null;
		try {
			/*
			 * TODO: Add java future here.
			 */
			FlowMap flowSpace = FVConfig.getFlowSpaceFlowMap();
			processFlows(params, flowSpace);
			FVLog.log(LogLevel.INFO, null,
					"Signalling FlowSpace Update to all event handlers");
			FlowSpaceImpl.getProxy().notifyChange(flowSpace);
			
			resp = new JSONRPC2Response(true, 0);
		} catch (ClassCastException e) {
			resp = new JSONRPC2Response(new JSONRPC2Error(JSONRPC2Error.INVALID_PARAMS.getCode(), 
					cmdName() + ": " + e.getMessage()), 0);
		} catch (MissingRequiredField e) {
			resp = new JSONRPC2Response(new JSONRPC2Error(JSONRPC2Error.INVALID_PARAMS.getCode(), 
					cmdName() + ": " + e.getMessage()), 0);
		} catch (ConfigError e) {
			resp = new JSONRPC2Response(new JSONRPC2Error(JSONRPC2Error.INTERNAL_ERROR.getCode(), 
					cmdName() + ": failed to insert flowspace entry" + e.getMessage()), 0);
		}
		return resp;
		
	}

	private void processFlows(List<Map<String, Object>> params, FlowMap flowSpace) 
			throws ClassCastException, MissingRequiredField, ConfigError {
		String name = null;
		Long dpid = null;
		Integer priority = null;
		FlowEntry fentry = null;
		String logMsg = null;
		for (Map<String,Object> fe : params) {
			name = HandlerUtils.<String>fetchField(FSNAME, fe, false, UUID.randomUUID().toString());
			dpid = FlowSpaceUtil
					.parseDPID(HandlerUtils.<String>fetchField(FlowSpace.DPID, fe, true, "any"));
			priority = HandlerUtils.<Integer>fetchField(FlowSpace.PRIO, fe, true, FlowEntry.DefaultPriority);
			FVMatch match = HandlerUtils.matchFromMap(
					HandlerUtils.<Map<String, Object>>fetchField(MATCH, fe, true, null));
			List<OFAction> sliceActions = parseSliceActions(
					HandlerUtils.<List<Map<String, Object>>>fetchField(SLICEACTIONS, fe, true, null));
			
			fentry = new FlowEntry(name, dpid, match, 0, priority, 
					(List<OFAction>) sliceActions);
			FlowSpaceImpl.getProxy().addRule(fentry);
			flowSpace.addRule(fentry);
			logMsg = "User " + APIUserCred.getUserName() + 
					flowspaceAddChangeLogMessage(fentry.getDpid(), 
							fentry.getRuleMatch(), fentry.getPriority(),
							fentry.getActionsList(), fentry.getName());
			FVLog.log(LogLevel.INFO, null, logMsg);
		}
		
	}
	
	

	private List<OFAction> parseSliceActions(List<Map<String, Object>> sactions) 
			throws ClassCastException, MissingRequiredField {
		List<OFAction> sa = new LinkedList<OFAction>();
		for (Map<String, Object> sact : sactions) {
			SliceAction sliceAction = new SliceAction(
					HandlerUtils.<String>fetchField(SLICENAME, sact, true, null),
					HandlerUtils.<Number>fetchField(PERM, sact, true, null).intValue());
			sa.add(sliceAction);
		}
		return sa;
	}

	@Override
	public JSONRPC2ParamsType getType() {
		return JSONRPC2ParamsType.ARRAY;
	}

	@Override
	public String cmdName() {
		return "add-flowspace";
	}
	
	
	private static String flowspaceAddChangeLogMessage(long dpid, OFMatch match,
			int priority, List<OFAction> actions, String name ){
		return " for dpid=" + FlowSpaceUtil.dpidToString(dpid) + " match=" + match +
		" priority=" + priority + " actions=" + FlowSpaceUtil.toString(actions) + " name=" + name;
	}

}
