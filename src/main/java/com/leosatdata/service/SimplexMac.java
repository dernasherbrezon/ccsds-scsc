package com.leosatdata.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.leosatdata.model.Event;
import com.leosatdata.model.MacState;
import com.leosatdata.model.Spdu;
import com.leosatdata.model.State;
import com.leosatdata.model.TransceiverOperationMode;
import com.leosatdata.util.Configuration;
import com.leosatdata.util.SpduUtil;
import com.leosatdata.util.TransceiverUtil;

public class SimplexMac {

	private final static Logger LOG = LoggerFactory.getLogger(SimplexMac.class);

	private final TimerService timer;
	private final Transceiver transceiver;
	private final TransceiverUtil transceiverUtil;
	private final State state;
	private final int spduVersion;

	public SimplexMac(State state, Configuration config, Transceiver transceiver, TimerService timer) {
		this.state = state;
		this.transceiver = transceiver;
		this.timer = timer;
		this.transceiverUtil = new TransceiverUtil(config, transceiver);
		this.spduVersion = config.getInteger("spdu_version", 1);
	}

	public void handle(Event e) {
		switch (e.getType()) {
		case LOCAL_COMM_CHANGE:
			if (e.getArg() == null) {
				LOG.error("received comm change without payload");
				return;
			}
			state.setCommParameters(SpduUtil.mergeCommParameters(state.getCommParameters(), (Spdu) e.getArg()));
			SpduUtil.setDuplex(state.getCommParameters(), state.getDuplex());
			break;
		case E71_TRANSMIT_SIMPLEX:
			if (state.getCommParameters() == null) {
				LOG.error("comm parameters not configured");
				return;
			}
			if (!SpduUtil.isValidDirective(spduVersion, state.getCommParameters())) {
				LOG.error("mismatch between provided comm parameters and spdu version: {}", spduVersion);
				return;
			}
			state.setTransceiverMode(TransceiverOperationMode.CALLER);
			transceiverUtil.applyTxParameters(state.getCommParameters(), state.getTransceiverMode().equals(TransceiverOperationMode.CALLER));
			// explicit rx off
			transceiver.setRxState(TransceiverRxState.OFF);
			transceiver.setTxState(TransceiverTxState.MODULATION);
			state.setState(MacState.S71_SIMPLEX_TRANSMIT);
			break;
		case E72_RECEIVE_SIMPLEX:
			if (state.getCommParameters() == null) {
				LOG.error("comm parameters not configured");
				return;
			}
			if (!SpduUtil.isValidDirective(spduVersion, state.getCommParameters())) {
				LOG.error("mismatch between provided comm parameters and spdu version: {}", spduVersion);
				return;
			}
			state.setTransceiverMode(TransceiverOperationMode.RESPONDER);
			transceiverUtil.applyRxParameters(state.getCommParameters(), state.getTransceiverMode().equals(TransceiverOperationMode.CALLER));
			transceiver.setRxState(TransceiverRxState.RECEIVING);
			// explicit tx off
			transceiver.setTxState(TransceiverTxState.OFF);
			state.setState(MacState.S72_SIMPLEX_RECEIVE);
			timer.startPeriodicPlcw();
			break;
		default:
			break;
		}
	}
}
