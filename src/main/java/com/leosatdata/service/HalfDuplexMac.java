package com.leosatdata.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.leosatdata.model.ControllerEvent;
import com.leosatdata.model.ControllerEventType;
import com.leosatdata.model.Event;
import com.leosatdata.model.EventType;
import com.leosatdata.model.MacState;
import com.leosatdata.model.PersistentActivity;
import com.leosatdata.model.Plcw;
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
	private int currentTokenPassAttempt = 0;

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
		handleTxDataExchange(e);
		handleRxDataExchange(e);
		// ensure comm change goes after rx/tx as it need to state shift to s50
		handleCommChange(e);
		handleTermination(e);
		handleReconnect(e);
	}

	@SuppressWarnings("incomplete-switch")
	private void handleRxDataExchange(Event e) {
		switch (e.getType()) {
		case E44_RECEIVE_TIMEOUT:
			if (state.getState().equals(MacState.S60_DATA_SERVICES_RECEIVE)) {
				if (carrierAcquired) {
					timer.scheduleWait(EventType.E44_RECEIVE_TIMEOUT, config.getDuration("receive_duration"));
					callback.handle(new ControllerEvent(ControllerEventType.SENDER_TX_TIMEOUT));
				} else {
					transceiver.setTxState(TransceiverTxState.CARRIER_ONLY);
					state.setState(MacState.S51_RADIATE_CARRIER);
					timer.scheduleWait(EventType.E40_CARRIER_ONLY_TIMEOUT, config.getDuration("carrier_only_duration"));
				}
			}
			if (state.getState().equals(MacState.S61_AWAITING_FIRST_FRAME)) {
				if (carrierAcquired) {
					timer.scheduleWait(EventType.E44_RECEIVE_TIMEOUT, config.getDuration("receive_duration"));
					callback.handle(new ControllerEvent(ControllerEventType.NO_DATA_RECEIVED));
				}
			}
			if (state.getState().equals(MacState.S61_AWAITING_FIRST_FRAME) || state.getState().equals(MacState.S62_WAIT_FOR_CARRIER)) {
				if (!carrierAcquired) {
					callback.handle(new ControllerEvent(ControllerEventType.NO_CARRIER_RECEIVED));
					transceiver.setTxState(TransceiverTxState.CARRIER_ONLY);
					state.setState(MacState.S51_RADIATE_CARRIER);
					timer.scheduleWait(EventType.E40_CARRIER_ONLY_TIMEOUT, config.getDuration("carrier_only_duration"));
				}
			}
			break;
		case CARRIER_ACQUIRED:
			if (state.getState().equals(MacState.S62_WAIT_FOR_CARRIER)) {
				state.setState(MacState.S61_AWAITING_FIRST_FRAME);
			}
			break;
		case SPDU:
			Spdu spdu = (Spdu) e.getArg();
			// explicit switch to tx by responder
			if (SpduUtil.isTransmit(spdu) && (state.getState().equals(MacState.S60_DATA_SERVICES_RECEIVE) || state.getState().equals(MacState.S61_AWAITING_FIRST_FRAME))) {
				transceiver.setTxState(TransceiverTxState.CARRIER_ONLY);
				state.setState(MacState.S51_RADIATE_CARRIER);
				timer.scheduleWait(EventType.E40_CARRIER_ONLY_TIMEOUT, config.getDuration("carrier_only_duration"));
			}
			// TODO track every received frame here? s61 only used for granular messages to
			// vehicle controller
			if (state.getState().equals(MacState.S61_AWAITING_FIRST_FRAME)) {
				state.setState(MacState.S60_DATA_SERVICES_RECEIVE);
			}
			break;
		}
	}

	@SuppressWarnings("incomplete-switch")
	private void handleTxDataExchange(Event e) {
		switch (e.getType()) {
		case E40_CARRIER_ONLY_TIMEOUT:
			transceiver.setTxState(TransceiverTxState.MODULATION);
			state.setNeedPlcw(true);
			state.setState(MacState.S52_RADIATE_ACQUISITION_IDLE);
			timer.scheduleWait(EventType.E41_ACQUISITION_IDLE_TIMEOUT, config.getDuration("acquisition_idle_duration"));
			break;
		case E41_ACQUISITION_IDLE_TIMEOUT:
			if (state.getState().equals(MacState.S52_RADIATE_ACQUISITION_IDLE)) {
				state.setState(MacState.S50_DATA_SERVICES_SEND);
				timer.scheduleWait(EventType.E38_SEND_TIMEOUT, config.getDuration("send_duration"));
			}
			break;
		case E38_SEND_TIMEOUT:
			if (state.getState().equals(MacState.S50_DATA_SERVICES_SEND)) {
				if (currentTokenPassAttempt > config.getInteger("maximum_failed_token_passes", 0)) {
					LOG.info("token pass exceeded, reconnect");
					sendEvent(new Event(EventType.E83_TOKEN_PASS_EXCEEDED));
					break;
				}
				state.setPersistence(true);
				// if already empty
				if (state.noSpduPending()) {
					sendEvent(new Event(EventType.E39_SWITCH_TO_TRANSMIT));
				} else {
					state.setState(MacState.S50_0_DATA_SERVICES_SEND);
				}
			}
			break;
		case E39_SWITCH_TO_TRANSMIT:
			currentTokenPassAttempt++;
			SpduUtil.setTransmit(state.getCommParameters(), true);
			state.setState(MacState.S56_TOKEN_PASS);
			state.getMacQueue().add(state.getCommParameters());
			break;
		case TX_COMPLETE:
			if (state.getState().equals(MacState.S50_0_DATA_SERVICES_SEND) && state.noSpduPending()) {
				sendEvent(new Event(EventType.E39_SWITCH_TO_TRANSMIT));
			}
			if (state.getState().equals(MacState.S56_TOKEN_PASS)) {
				state.setState(MacState.S58_TAIL_BEFORE_SWITCH);
				timer.scheduleWait(EventType.E43_TAIL_IDLE_TIMEOUT, config.getDuration("tail_idle_duration"));
			}
			break;
		case E43_TAIL_IDLE_TIMEOUT:
			if (state.getState().equals(MacState.S58_TAIL_BEFORE_SWITCH)) {
				state.setPersistence(false);
				state.setState(MacState.S62_WAIT_FOR_CARRIER);
				transceiver.setRxState(TransceiverRxState.RECEIVING);
				timer.scheduleWait(EventType.E44_RECEIVE_TIMEOUT, config.getDuration("receive_duration"));
			}
			break;
		}
	}

	@SuppressWarnings("incomplete-switch")
	private void handleHail(Event e) {
		switch (e.getType()) {
		case E29_CONNECTING_L:
			LOG.info("waiting for hail");
			state.setState(MacState.S2_WAITING_FOR_HAIL);
			state.setTransceiverMode(TransceiverOperationMode.RESPONDER);
			Spdu hailCommParameters = transceiverUtil.createHailTxRx();
			SpduUtil.setDuplex(hailCommParameters, state.getDuplex());
			transceiverUtil.applyRxParameters(hailCommParameters, state.getTransceiverMode().equals(TransceiverOperationMode.CALLER));
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
				LOG.info("session established");
				LOG.info("data services ready");
				timer.scheduleWait(EventType.E44_RECEIVE_TIMEOUT, config.getDuration("receive_duration"));
				state.setState(MacState.S60_DATA_SERVICES_RECEIVE);
			}
			if (state.getState().equals(MacState.S2_WAITING_FOR_HAIL)) {
				if (!SpduUtil.isValidDirective(spduVersion, spdu) || !SpduUtil.isCommChange(spdu)) {
					break;
				}
				callback.handle(createNotification("hail", true));

				state.setCommParameters(spdu);

				transceiverUtil.applyParameters(state.getCommParameters(), state.getTransceiverMode().equals(TransceiverOperationMode.CALLER));
				transceiver.setTxState(TransceiverTxState.CARRIER_ONLY);
				state.setState(MacState.S51_RADIATE_CARRIER);
				timer.scheduleWait(EventType.E40_CARRIER_ONLY_TIMEOUT, config.getDuration("carrier_only_duration"));
				LOG.info("session established");
				LOG.info("data services ready");
			}
			break;
		case CARRIER_ACQUIRED:
			carrierAcquired = true;
			timer.cancelCarrierLoss();
			break;
		case CARRIER_LOST:
			carrierAcquired = false;
			if (state.getState().equals(MacState.S60_DATA_SERVICES_RECEIVE)) {
				timer.scheduleCarrierLossTimeout(EventType.E85_CARRIER_LOST_TIMEOUT, config.getDuration("carrier_loss_timer_duration"));
			}
			break;
		}
	}

	@SuppressWarnings("incomplete-switch")
	private void handleTermination(Event e) {
		switch (e.getType()) {
		case E51_LNMD:
			// initiate lnmd
			if (state.getX() == 0) {
				state.setX(1);
			}
			// remote already indicated no more data. transition to the local no more data
			if (state.getX() == 3) {
				state.setX(4);
			}
			break;
		case E41_ACQUISITION_IDLE_TIMEOUT:
			// just before switching to s50 check if rnmd was received
			if (state.getState().equals(MacState.S52_RADIATE_ACQUISITION_IDLE)) {
				checkIfTerminationNeededAndSend();
			}
			break;
		case E57_TAIL_IDLE_TIMEOUT:
			putIntoInactive();
			break;
		case TX_COMPLETE:
			if (state.getState().equals(MacState.S50_0_DATA_SERVICES_SEND) || state.getState().equals(MacState.S50_0_DATA_SERVICES_SEND)) {
				checkIfTerminationNeededAndSend();
			}
			if (state.getState().equals(MacState.S54_TERMINATION_FROM_REMOTE) && state.getX() == 5) {
				timer.scheduleWait(EventType.E57_TAIL_IDLE_TIMEOUT, config.getDuration("tail_idle_duration"));
			}
			break;
		case SPDU:
			if (e.getArg() instanceof Plcw) {
				break;
			}
			boolean rnmd = SpduUtil.isRnmd((Spdu) e.getArg());
			if (!rnmd) {
				break;
			}
			if (state.getState().equals(MacState.S60_DATA_SERVICES_RECEIVE) || state.getState().equals(MacState.S61_AWAITING_FIRST_FRAME)) {
				// this is response from caller initiation
				if (state.getX() == 2) {
					putIntoInactive();
				}
				// this termination initiated remotely
				// - during normal receive
				if (state.getX() == 0) {
					state.setX(3);
					transceiver.setTxState(TransceiverTxState.CARRIER_ONLY);
					state.setState(MacState.S51_RADIATE_CARRIER);
					timer.scheduleWait(EventType.E40_CARRIER_ONLY_TIMEOUT, config.getDuration("carrier_only_duration"));
				}
				// - just before entering receive
				if (state.getX() == 1) {
					state.setX(4);
					transceiver.setTxState(TransceiverTxState.CARRIER_ONLY);
					state.setState(MacState.S51_RADIATE_CARRIER);
					timer.scheduleWait(EventType.E40_CARRIER_ONLY_TIMEOUT, config.getDuration("carrier_only_duration"));
				}
			}
			break;
		}
	}

	private void putIntoInactive() {
		state.setX(0);
		LOG.info("session stopped");
		// FIXME reset state variables, turn off transceiver
		state.setState(MacState.S1_INACTIVE);
		// don't send number of received packets. not clear why and hard to sync rx
		// thread data
		callback.handle(new ControllerEvent(ControllerEventType.END_OF_SESSION));
	}

	private void checkIfTerminationNeededAndSend() {
		if (!state.noFramesPending()) {
			return;
		}
		Spdu rnmdDirective = SpduUtil.createRnmdDirective(state.getCommParameters(), true);
		if (rnmdDirective == null) {
			LOG.error("unable to create rnmd - not enough data in comm parameters");
			return;
		}
		// this termination initiated locally
		// - send directive to responder
		if (state.getX() == 1) {
			state.getMacQueue().add(rnmdDirective);
			state.setX(2);
		}
		// this is reply to the remote termination
		if (state.getX() == 4) {
			state.getMacQueue().add(rnmdDirective);
			state.setState(MacState.S54_TERMINATION_FROM_REMOTE);
			state.setX(5);
		}
	}

	@SuppressWarnings("incomplete-switch")
	private void handleReconnect(Event e) {
		switch (e.getType()) {
		case E83_TOKEN_PASS_EXCEEDED:
			if (state.getState().equals(MacState.S50_0_DATA_SERVICES_SEND) || state.getState().equals(MacState.S50_DATA_SERVICES_SEND)) {
				state.setState(MacState.S80_RECONNECT);
				timer.scheduleWait(EventType.E84_RECONNECT_WAIT_TIMEOUT, config.getDuration("reconnect_wait_duration"));
				transceiver.setTxState(TransceiverTxState.OFF);
			}
			break;
		case E84_RECONNECT_WAIT_TIMEOUT:
			if (state.getState().equals(MacState.S80_RECONNECT)) {
				timer.stopPeriodicPlcw();
				sendEvent(new Event(EventType.E31_CONNECTING_T));
			}
			break;
		case E85_CARRIER_LOST_TIMEOUT:
			if (state.getState().equals(MacState.S60_DATA_SERVICES_RECEIVE)) {
				timer.stopPeriodicPlcw();
				sendEvent(new Event(EventType.E29_CONNECTING_L));
			}
			break;
		}
	}

	@SuppressWarnings("incomplete-switch")
	private void handleCommChange(Event e) {
		switch (e.getType()) {
		case E62_LOCAL_COMM_CHANGE:
			if (e.getArg() == null) {
				LOG.error("received comm change without payload");
				return;
			}
			state.setCommParameters(SpduUtil.mergeCommParameters(state.getCommParameters(), (Spdu) e.getArg()));
			SpduUtil.setDuplex(state.getCommParameters(), state.getDuplex());
			if (state.getState().equals(MacState.S50_DATA_SERVICES_SEND) || state.getState().equals(MacState.S50_0_DATA_SERVICES_SEND)) {
				initiateCommChange();
			} else {
				state.setCommChangeRequested(true);
			}
			break;
		case E41_ACQUISITION_IDLE_TIMEOUT:
			if (state.isCommChangeRequested()) {
				initiateCommChange();
			}
			break;
		case TX_COMPLETE:
			if (state.getState().equals(MacState.S50_2_DATA_SERVICES_SEND) && state.noSpduPending()) {
				state.getMacQueue().add(state.getCommParameters());
				state.setState(MacState.S56_2_COMM_CHANGE);
			}
			// comm change has been sent
			if (state.getState().equals(MacState.S56_2_COMM_CHANGE)) {
				state.setState(MacState.S58_2_TAIL_BEFORE_SWITCH);
				timer.scheduleWait(EventType.E67_TAIL_IDLE_TIMEOUT, config.getDuration("tail_idle_duration"));
			}
			break;
		case E67_TAIL_IDLE_TIMEOUT:
			if (state.getState().equals(MacState.S58_2_TAIL_BEFORE_SWITCH)) {
				state.setState(MacState.S62_3_WAIT_FOR_CARRIER);
				transceiver.setRxState(TransceiverRxState.RECEIVING);
				timer.scheduleWait(EventType.E44_RECEIVE_TIMEOUT, config.getDuration("receive_duration"));
			}
			break;
		case CARRIER_ACQUIRED:
			if (state.getState().equals(MacState.S62_3_WAIT_FOR_CARRIER)) {
				state.setState(MacState.S61_3_AWAITING_FIRST_FRAME);
			}
			break;
		case SPDU:
			Spdu spdu = (Spdu) e.getArg();
			if (state.getState().equals(MacState.S61_3_AWAITING_FIRST_FRAME) || state.getState().equals(MacState.S62_3_WAIT_FOR_CARRIER)) {
				state.setState(MacState.S60_DATA_SERVICES_RECEIVE);
				transceiverUtil.applyTxParameters(state.getCommParameters(), state.getTransceiverMode().equals(TransceiverOperationMode.CALLER));
				LOG.info("tx comm parameters changed: {}", state.getCommParameters());
				state.setPersistence(false);
			}
			if (SpduUtil.isCommChange(spdu) && (state.getState().equals(MacState.S60_DATA_SERVICES_RECEIVE) || state.getState().equals(MacState.S61_AWAITING_FIRST_FRAME))) {
				state.setCommParameters(SpduUtil.mergeCommParameters(state.getCommParameters(), spdu));
				// FIXME switch mode if changed
				state.setDuplex(SpduUtil.getDuplex(state.getCommParameters()));
				transceiverUtil.applyParameters(state.getCommParameters(), state.getTransceiverMode().equals(TransceiverOperationMode.CALLER));
				state.setNeedPlcw(true);
			}
			break;
		}
	}

	private void initiateCommChange() {
		state.setPersistence(true);
		transceiverUtil.applyRxParameters(state.getCommParameters(), state.getTransceiverMode().equals(TransceiverOperationMode.CALLER));
		LOG.info("rx comm parameters changed: {}", state.getCommParameters());
		// ready to send
		if (state.noSpduPending()) {
			state.getMacQueue().add(state.getCommParameters());
			state.setState(MacState.S56_2_COMM_CHANGE);
		} else {
			state.setState(MacState.S50_2_DATA_SERVICES_SEND);
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
