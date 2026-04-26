package com.leosatdata.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.leosatdata.model.ControllerEvent;
import com.leosatdata.model.ControllerEventType;
import com.leosatdata.model.Event;
import com.leosatdata.model.EventType;
import com.leosatdata.model.MacState;
import com.leosatdata.model.PersistentActivity;
import com.leosatdata.model.Spdu;
import com.leosatdata.model.State;
import com.leosatdata.model.TransceiverOperationMode;
import com.leosatdata.util.Configuration;
import com.leosatdata.util.SpduUtil;
import com.leosatdata.util.TransceiverUtil;

public class HalfDuplexMac {

	private final static Logger LOG = LoggerFactory.getLogger(HalfDuplexMac.class);

	private final State state;
	private final Configuration config;
	private final Transceiver transceiver;
	private final TransceiverUtil transceiverUtil;
	private final NodeCallback callback;
	private final TimerService timer;
	private final int spduVersion;

	private int currentHailAttempt = 0;
	private int currentCommChangeAttempt = 0;
	private boolean carrierAcquired;

	public HalfDuplexMac(State state, Configuration config, Transceiver transceiver, NodeCallback callback, TimerService timer) {
		this.state = state;
		this.config = config;
		this.transceiver = transceiver;
		this.transceiverUtil = new TransceiverUtil(config, transceiver);
		this.callback = callback;
		this.timer = timer;
		this.spduVersion = config.getInteger("spdu_version", 1);
	}

	public void handle(Event e) {
		handleHail(e);
		handleTermination(e);
	}

	@SuppressWarnings("incomplete-switch")
	private void handleHail(Event e) {
		switch (e.getType()) {
		case E29_CONNECTING_L:
			LOG.info("waiting for hail");
			// FIXME persistence?
//			state.setPersistence(true);
			state.setState(MacState.S2_WAITING_FOR_HAIL_REQUEST);
			state.setTransceiverMode(TransceiverOperationMode.RESPONDER);
			Spdu hailCommParameters = transceiverUtil.createHailTxRx();
			SpduUtil.setDuplex(hailCommParameters, state.getDuplex());
			transceiverUtil.applyRxParameters(hailCommParameters, state.getTransceiverMode().equals(TransceiverOperationMode.CALLER));
			transceiver.setTxState(TransceiverTxState.OFF);
			transceiver.setRxState(TransceiverRxState.RECEIVING);
			break;
		case E31_CONNECTING_T:
			if (state.getCommParameters() == null) {
				LOG.error("comm parameters not configured");
				callback.handle(createNotification("hail", false));
				return;
			}
			if (!SpduUtil.isValidDirective(spduVersion, state.getCommParameters())) {
				LOG.error("mismatch between provided comm parameters and spdu version: {}", spduVersion);
				callback.handle(createNotification("hail", false));
				return;
			}
			LOG.info("attempting hail: {} of {}", (currentHailAttempt + 1), config.getInteger("hail_lifetime", 0));
			state.setTransceiverMode(TransceiverOperationMode.CALLER);

			transceiverUtil.applyRxParameters(state.getCommParameters(), state.getTransceiverMode().equals(TransceiverOperationMode.CALLER));

			hailCommParameters = transceiverUtil.createHailTxRx();
			SpduUtil.setDuplex(hailCommParameters, state.getDuplex());
			transceiverUtil.applyTxParameters(hailCommParameters, state.getTransceiverMode().equals(TransceiverOperationMode.CALLER));
			transceiver.setRxState(TransceiverRxState.OFF);
			transceiver.setTxState(TransceiverTxState.CARRIER_ONLY);
			state.setPersistence(true);
			state.setState(MacState.S11_START_HAIL);
			timer.scheduleWait(EventType.E32_CARRIER_ONLY_TIMEOUT, config.getDuration("carrier_only_duration"));
			break;
		case E32_CARRIER_ONLY_TIMEOUT:
			transceiver.setTxState(TransceiverTxState.MODULATION);
			state.setState(MacState.S12_SEND_HAIL_ACQUISITION);
			timer.scheduleWait(EventType.E33_ACQUISITION_IDLE_TIMEOUT, config.getDuration("acquisition_idle_duration"));
			break;
		case E33_ACQUISITION_IDLE_TIMEOUT:
			state.getMacQueue().add(state.getCommParameters());
			state.setState(MacState.S13_SEND_HAIL_DIRECTIVES);
			break;
		case TX_COMPLETE:
			if (state.getState().equals(MacState.S13_SEND_HAIL_DIRECTIVES)) {
				state.setState(MacState.S14_SEND_HAIL_TAIL);
				timer.scheduleWait(EventType.E35_TAIL_IDLE_TIMEOUT, config.getDuration("tail_idle_duration"));
			}
			break;
		case E35_TAIL_IDLE_TIMEOUT:
			carrierAcquired = false;
			state.setState(MacState.S36_WAITING_FOR_HAIL_RESPONSE);
			transceiver.setTxState(TransceiverTxState.OFF);
			transceiver.setRxState(TransceiverRxState.RECEIVING);
			timer.scheduleWait(EventType.E36_HAIL_WAIT_TIMEOUT, config.getDuration("hail_wait_duration"));
			break;
		case E36_HAIL_WAIT_TIMEOUT:
			if (carrierAcquired) {
				callback.handle(new ControllerEvent(ControllerEventType.CARRIER_ONLY_RECEIVED));
			}
			currentHailAttempt++;
			int maxAttempts = config.getInteger("hail_lifetime", 0);
			if (maxAttempts != 0 && currentHailAttempt >= maxAttempts) {
				currentHailAttempt = 0;
				callback.handle(createNotification("hail", false));
			} else {
				sendEvent(new Event(EventType.E31_CONNECTING_T));
			}
			break;
		case SPDU:
			Spdu spdu = (Spdu) e.getArg();
			// check several states because responder can send response faster than extra
			// idle and carrier timeouts
			if (state.getState().equals(MacState.S36_WAITING_FOR_HAIL_RESPONSE) || state.getState().equals(MacState.S14_SEND_HAIL_TAIL)) {
				callback.handle(createNotification("hail", true));
				currentHailAttempt = 0;

				// switch over to the agreed link
				transceiverUtil.applyTxParameters(state.getCommParameters(), state.getTransceiverMode().equals(TransceiverOperationMode.CALLER));

				state.setPersistence(false);

				timer.cancelWait();
				timer.scheduleWait(EventType.E44_RECEIVE_TIMEOUT, config.getDuration("receive_duration"));
				LOG.info("session established");
				LOG.info("data services ready");
			}
			if (state.getState().equals(MacState.S2_WAITING_FOR_HAIL_REQUEST)) {
				if (!SpduUtil.isValidDirective(spduVersion, spdu) || !SpduUtil.isCommChange(spdu)) {
					break;
				}
				callback.handle(createNotification("hail", true));

				state.setCommParameters(spdu);

				transceiverUtil.applyParameters(state.getCommParameters(), state.getTransceiverMode().equals(TransceiverOperationMode.CALLER));
				transceiver.setRxState(TransceiverRxState.OFF);
				transceiver.setTxState(TransceiverTxState.CARRIER_ONLY);
				timer.scheduleWait(EventType.E40_CARRIER_ONLY_TIMEOUT, config.getDuration("carrier_only_duration"));
				LOG.info("session established");
			}
			break;
		case E40_CARRIER_ONLY_TIMEOUT:
			transceiver.setTxState(TransceiverTxState.MODULATION);
			state.setNeedPlcw(true);
			timer.scheduleWait(EventType.E41_ACQUISITION_IDLE_TIMEOUT, config.getDuration("acquisition_idle_duration"));
			break;
		case E41_ACQUISITION_IDLE_TIMEOUT:
			LOG.info("data services ready");
			//FIXME send duration
			break;
		case E44_RECEIVE_TIMEOUT:
			if (carrierAcquired) {
				callback.handle(new ControllerEvent(ControllerEventType.SENDER_TX_TIMEOUT));
			}
			break;
		case CARRIER_ACQUIRED:
			carrierAcquired = true;
			timer.cancelCarrierLoss();
			break;
		case CARRIER_LOST:
			// FIXME
			break;
		}
	}

	@SuppressWarnings("incomplete-switch")
	private void handleTermination(Event e) {
		switch (e.getType()) {

		}
	}

	private ControllerEvent createNotification(String prefix, boolean success) {
		PersistentActivity activity = new PersistentActivity();
		activity.setSuccess(success);
		activity.setLifetime(config.getInteger(prefix + "_lifetime", 0));
		activity.setWaitingPeriod(config.getInteger(prefix + "_wait_duration", 0));
		activity.setNotification(config.getInteger(prefix + "_notification", 0));
		activity.setResponse(config.getInteger(prefix + "_response", 0));
		return new ControllerEvent(ControllerEventType.PERSISTENT_ACTIVITY, activity);
	}

	private void sendEvent(Event e) {
		try {
			state.getQueue().put(e);
		} catch (InterruptedException e1) {
			Thread.currentThread().interrupt();
			LOG.error("can't send event", e1);
		}
	}
}
