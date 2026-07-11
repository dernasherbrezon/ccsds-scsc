package com.leosatdata.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.leosatdata.model.ControllerEvent;
import com.leosatdata.model.ControllerEventType;
import com.leosatdata.model.Event;
import com.leosatdata.model.EventType;
import com.leosatdata.model.MacState;
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
		// ensure termination goes before rx/tx
		handleTermination(e);
		handleTxDataExchange(e);
		handleRxDataExchange(e);
		// ensure comm change goes after rx/tx as it need to state shift to s50
		handleCommChange(e);
		handleReconnect(e);
	}

	@SuppressWarnings("incomplete-switch")
	private void handleRxDataExchange(Event e) {
		switch (e.getType()) {
		case E44_RECEIVE_TIMEOUT:
			if (state.getState().equals(MacState.S60_DATA_SERVICES_RECEIVE)) {
				if (state.isCarrierAcquired()) {
					timer.scheduleWait(EventType.E44_RECEIVE_TIMEOUT, config.getDuration("receive_duration"));
					callback.handle(new ControllerEvent(ControllerEventType.SENDER_TX_TIMEOUT));
				} else {
					switchToTx();
				}
			}
			if (state.getState().equals(MacState.S61_AWAITING_FIRST_FRAME)) {
				if (state.isCarrierAcquired()) {
					timer.scheduleWait(EventType.E44_RECEIVE_TIMEOUT, config.getDuration("receive_duration"));
					callback.handle(new ControllerEvent(ControllerEventType.NO_DATA_RECEIVED));
				}
			}
			if (state.getState().equals(MacState.S61_AWAITING_FIRST_FRAME) || state.getState().equals(MacState.S62_WAIT_FOR_CARRIER)) {
				if (!state.isCarrierAcquired()) {
					callback.handle(new ControllerEvent(ControllerEventType.NO_CARRIER_RECEIVED));
					switchToTx();
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
				// cancel E44_RECEIVE_TIMEOUT
				timer.cancelWait();
				switchToTx();
			}
			// TODO track every received frame here? s61 only used for granular messages to
			// vehicle controller
			if (state.getState().equals(MacState.S61_AWAITING_FIRST_FRAME)) {
				// switch from tx was successfull, reset token
				currentTokenPassAttempt = 0;
				state.setState(MacState.S60_DATA_SERVICES_RECEIVE);
			}
			break;
		}
	}

	@SuppressWarnings("incomplete-switch")
	private void handleTxDataExchange(Event e) {
		switch (e.getType()) {
		case E40_CARRIER_ONLY_TIMEOUT:
			state.setNeedPlcw(true);
			state.setState(MacState.S52_RADIATE_ACQUISITION_IDLE);
			timer.scheduleWait(EventType.E41_ACQUISITION_IDLE_TIMEOUT, config.getDuration("acquisition_idle_duration"));
			break;
		case E41_ACQUISITION_IDLE_TIMEOUT:
			if (state.getState().equals(MacState.S52_RADIATE_ACQUISITION_IDLE)) {
				// not according to spec. as soon modulation is set it will output pending
				// frames
				transceiver.setTxState(TransceiverTxState.MODULATION);
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
				if (state.noSpduPending() && state.isTxReady()) {
					sendTransmitToken();
				} else {
					state.setState(MacState.S50_DATA_SERVICES_SEND);
				}
			}
			break;
		case TX_COMPLETE:
			if (state.getState().equals(MacState.S50_DATA_SERVICES_SEND)) {
				// no more user's frames to send, switch to receive before reaching send_timeout
				boolean noMoreUserFramesToSend = state.noFramesPending() && !state.isPersistence();
				// persistence=true indicate that send_timeout happened
				// IMPORTANT: check only mac queue, do not check need plcw. If large message was
				// sent and it takes more time than send_timeout, then switch to rx never happen
				boolean switchAfterSendTimeout = state.getMacQueue().isEmpty() && state.isPersistence();
				if (noMoreUserFramesToSend || switchAfterSendTimeout) {
					state.setPersistence(true);
					// cancel E38_SEND_TIMEOUT
					timer.cancelWait();
					sendTransmitToken();
				}
			}
			if (state.getState().equals(MacState.S56_TOKEN_PASS)) {
				state.setState(MacState.S58_TAIL_BEFORE_SWITCH);
				timer.scheduleWait(EventType.E43_TAIL_IDLE_TIMEOUT, config.getDuration("tail_idle_duration"));
			}
			break;
		case E43_TAIL_IDLE_TIMEOUT:
			if (state.getState().equals(MacState.S58_TAIL_BEFORE_SWITCH)) {
				state.setPersistence(false);
				switchToRx();
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
			transceiver.setTxState(TransceiverTxState.OFF);
			transceiver.setRxState(TransceiverRxState.RECEIVING);
			break;
		case E31_CONNECTING_T:
			if (state.getCommParameters() == null) {
				callback.handle(config.createNotification("hail", "comm parameters not configured"));
				return;
			}
			if (!SpduUtil.isCommDirective(spduVersion, state.getCommParameters())) {
				callback.handle(config.createNotification("hail", "mismatch between provided comm parameters and spdu version"));
				return;
			}
			LOG.info("attempting hail: {} of {}", (currentHailAttempt + 1), config.getInteger("hail_lifetime", 0));
			state.setTransceiverMode(TransceiverOperationMode.CALLER);

			transceiverUtil.applyRxParameters(state.getCommParameters(), state.getTransceiverMode().equals(TransceiverOperationMode.CALLER));

			hailCommParameters = transceiverUtil.createHailTxRx();
			SpduUtil.setDuplex(hailCommParameters, state.getDuplex());
			// switch from rx to tx is implied by hail state machine, put here for extra
			// clarity/safety
			SpduUtil.setTransmit(hailCommParameters, true);
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
			state.setState(MacState.S36_WAITING_FOR_HAIL_RESPONSE);
			transceiver.setTxState(TransceiverTxState.OFF);
			transceiver.setRxState(TransceiverRxState.RECEIVING);
			timer.scheduleWait(EventType.E36_HAIL_WAIT_TIMEOUT, config.getDuration("hail_wait_duration"));
			break;
		case E36_HAIL_WAIT_TIMEOUT:
			if (state.isCarrierAcquired()) {
				callback.handle(new ControllerEvent(ControllerEventType.CARRIER_ONLY_RECEIVED));
			}
			currentHailAttempt++;
			int maxAttempts = config.getInteger("hail_lifetime", 0);
			if (maxAttempts != 0 && currentHailAttempt >= maxAttempts) {
				currentHailAttempt = 0;
				callback.handle(config.createNotification("hail", "retry failed"));
			} else {
				sendEvent(new Event(EventType.E31_CONNECTING_T));
			}
			break;
		case SPDU:
			Spdu spdu = (Spdu) e.getArg();
			// check several states because responder can send response faster than extra
			// idle and carrier timeouts
			if (state.getState().equals(MacState.S36_WAITING_FOR_HAIL_RESPONSE) || state.getState().equals(MacState.S14_SEND_HAIL_TAIL)) {
				callback.handle(config.createNotification("hail"));
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
				if (!SpduUtil.isCommDirective(spduVersion, spdu) || !SpduUtil.isCommChange(spdu)) {
					break;
				}
				callback.handle(config.createNotification("hail"));

				state.setCommParameters(spdu);

				transceiverUtil.applyParameters(state.getCommParameters(), state.getTransceiverMode().equals(TransceiverOperationMode.CALLER));
				transceiver.setRxState(TransceiverRxState.OFF);
				transceiver.setTxState(TransceiverTxState.CARRIER_ONLY);
				state.setState(MacState.S51_RADIATE_CARRIER);
				timer.scheduleWait(EventType.E40_CARRIER_ONLY_TIMEOUT, config.getDuration("carrier_only_duration"));
				LOG.info("session established");
				LOG.info("data services ready");
			}
			break;
		case CARRIER_LOST:
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
				LOG.info("session is stopping");
				state.setX(1);
				// if no more frames to send
				if (state.getState().equals(MacState.S50_DATA_SERVICES_SEND)) {
					checkIfTerminationNeededAndSend();
				}
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
			if (state.getState().equals(MacState.S55_TAIL_BEFORE_QUIT)) {
				putIntoInactive();
			}
			break;
		case TX_COMPLETE:
			if (state.getState().equals(MacState.S50_DATA_SERVICES_SEND)) {
				checkIfTerminationNeededAndSend();
			}
			if (state.getState().equals(MacState.S54_TERMINATION_FROM_REMOTE) && state.getX() == 5 && state.noFramesPending()) {
				state.setState(MacState.S55_TAIL_BEFORE_QUIT);
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
				// this termination initiated remotely
				// - during normal receive
				if (state.getX() == 0) {
					LOG.info("session is stopping");
					state.setX(3);
					switchToTx();
				} else if (state.getX() == 1) {
					// - just before entering receive
					state.setX(4);
					switchToTx();
				} else if (state.getX() == 2) {
					// this is response from caller initiation
					putIntoInactive();
				}
			}
			break;
		}
	}

	@SuppressWarnings("incomplete-switch")
	private void handleReconnect(Event e) {
		switch (e.getType()) {
		case E83_TOKEN_PASS_EXCEEDED:
			if (state.getState().equals(MacState.S50_DATA_SERVICES_SEND)) {
				state.setState(MacState.S80_RECONNECT);
				timer.scheduleWait(EventType.E84_RECONNECT_WAIT_TIMEOUT, config.getDuration("reconnect_wait_duration"));
				transceiver.setTxState(TransceiverTxState.OFF);
			}
			break;
		case E84_RECONNECT_WAIT_TIMEOUT:
			if (state.getState().equals(MacState.S80_RECONNECT)) {
				timer.stopPeriodicPlcw();
				startHail();
			}
			break;
		case CARRIER_LOST:
			// CARRIER_LOSS_TIMER value is ‘0’ and MODE = active;
			if (state.getState().equals(MacState.S60_DATA_SERVICES_RECEIVE) || state.getState().equals(MacState.S61_AWAITING_FIRST_FRAME)) {
				timer.scheduleCarrierLossTimeout(EventType.E85_CARRIER_LOST_TIMEOUT, config.getDuration("carrier_loss_timer_duration"));
			}
			break;
		case E85_CARRIER_LOST_TIMEOUT:
			if (state.getState().equals(MacState.S60_DATA_SERVICES_RECEIVE) || state.getState().equals(MacState.S61_AWAITING_FIRST_FRAME)) {
				LOG.info("carrier loss recovery timeout");
				timer.cancelCarrierLoss();
				callback.handle(new ControllerEvent(ControllerEventType.CARRIER_LOSS));
				timer.stopPeriodicPlcw();
				startHail();
			}
			break;
		}
	}

	@SuppressWarnings("incomplete-switch")
	private void handleCommChange(Event e) {
		// just switched into s50
		if (state.getState().equals(MacState.S50_DATA_SERVICES_SEND) && (state.getY() == 1 || state.getY() == 3)) {
			initiateCommChange();
		}
		switch (e.getType()) {
		case E62_LOCAL_COMM_CHANGE:
			if (e.getArg() == null) {
				LOG.error("received comm change without payload");
				return;
			}
			state.setCommParameters(SpduUtil.mergeCommParameters(state.getCommParameters(), (Spdu) e.getArg()));
			SpduUtil.setDuplex(state.getCommParameters(), state.getDuplex());
			// not the actual comm change
			if (state.getState().equals(MacState.S1_INACTIVE)) {
				break;
			}
			if (state.getState().equals(MacState.S50_DATA_SERVICES_SEND) && state.getY() == 0) {
				initiateCommChange();
			} else {
				state.setY(1);
			}
			break;
		case TX_COMPLETE:
			if (state.getState().equals(MacState.S50_DATA_SERVICES_SEND) && state.getY() == 2 && state.noSpduPending()) {
				state.getMacQueue().add(state.getCommParameters());
				state.setState(MacState.S56_COMM_CHANGE);
			}
			// comm change has been sent
			if (state.getState().equals(MacState.S56_COMM_CHANGE) && state.getY() == 2) {
				state.setState(MacState.S58_TAIL_BEFORE_SWITCH);
				timer.scheduleWait(EventType.E67_TAIL_IDLE_TIMEOUT, config.getDuration("tail_idle_duration"));
			}
			break;
		case E67_TAIL_IDLE_TIMEOUT:
			if (state.getState().equals(MacState.S58_TAIL_BEFORE_SWITCH) && state.getY() == 2) {
				state.setY(3);
				switchToRx();
			}
			break;
		case CARRIER_ACQUIRED:
			if (state.getState().equals(MacState.S62_WAIT_FOR_CARRIER)) {
				state.setState(MacState.S61_AWAITING_FIRST_FRAME);
			}
			break;
		case SPDU:
			Spdu spdu = (Spdu) e.getArg();
			if ((state.getState().equals(MacState.S60_DATA_SERVICES_RECEIVE) || state.getState().equals(MacState.S61_AWAITING_FIRST_FRAME)) && state.getY() == 3) {
				state.setState(MacState.S60_DATA_SERVICES_RECEIVE);
				state.setY(0);
				transceiverUtil.applyTxParameters(state.getCommParameters(), state.getTransceiverMode().equals(TransceiverOperationMode.CALLER));
				LOG.info("tx comm parameters changed: {}", state.getCommParameters());
				currentCommChangeAttempt = 0;
				callback.handle(config.createNotification("comm_change"));
				state.setPersistence(false);
			}
			if (SpduUtil.isCommChange(spdu) && (state.getState().equals(MacState.S60_DATA_SERVICES_RECEIVE) || state.getState().equals(MacState.S61_AWAITING_FIRST_FRAME))) {
				state.setCommParameters(SpduUtil.mergeCommParameters(state.getCommParameters(), spdu));
				// FIXME switch mode if changed
				state.setDuplex(SpduUtil.getDuplex(state.getCommParameters()));
				transceiverUtil.applyParameters(state.getCommParameters(), state.getTransceiverMode().equals(TransceiverOperationMode.CALLER));
				LOG.info("rx/tx comm parameters changed: {}", state.getCommParameters());
				currentCommChangeAttempt = 0;
				callback.handle(config.createNotification("comm_change"));
				state.setNeedPlcw(true);
				switchToTx();
			}
			break;
		}
	}

	private void putIntoInactive() {
		LOG.info("session stopped");
		sendEvent(new Event(EventType.E28_INACTIVE));
	}

	private void checkIfTerminationNeededAndSend() {
		// shortcut
		if (state.getX() != 1 && state.getX() != 4) {
			return;
		}
		// FIXME check txReady?
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

	private void startHail() {
		switch (state.getTransceiverMode()) {
		case CALLER:
			sendEvent(new Event(EventType.E31_CONNECTING_T));
			break;
		case RESPONDER:
			sendEvent(new Event(EventType.E29_CONNECTING_L));
			break;
		default:
			LOG.error("unsupported transceiver operating mode: {}", state.getTransceiverMode());
			break;
		}
	}

	private void sendTransmitToken() {
		currentTokenPassAttempt++;
		state.setState(MacState.S56_TOKEN_PASS);
		state.getMacQueue().add(SpduUtil.createTransmit(state.getCommParameters(), true));
	}

	private void switchToRx() {
		state.setState(MacState.S62_WAIT_FOR_CARRIER);
		transceiver.setTxState(TransceiverTxState.OFF);
		transceiver.setRxState(TransceiverRxState.RECEIVING);
		timer.scheduleWait(EventType.E44_RECEIVE_TIMEOUT, config.getDuration("receive_duration"));
	}

	private void switchToTx() {
		transceiver.setTxState(TransceiverTxState.CARRIER_ONLY);
		state.setState(MacState.S51_RADIATE_CARRIER);
		timer.scheduleWait(EventType.E40_CARRIER_ONLY_TIMEOUT, config.getDuration("carrier_only_duration"));
	}

	private void initiateCommChange() {
		currentCommChangeAttempt++;
		int maxAttempts = config.getInteger("comm_change_lifetime", 0);
		if (maxAttempts != 0 && currentCommChangeAttempt >= maxAttempts) {
			currentCommChangeAttempt = 0;
			callback.handle(config.createNotification("comm_change", "retry failed"));
			// ignore further comm change attempts
			state.setY(0);
			return;
		}
		state.setPersistence(true);
		state.setY(2);
		transceiverUtil.applyRxParameters(state.getCommParameters(), state.getTransceiverMode().equals(TransceiverOperationMode.CALLER));
		LOG.info("rx comm parameters changed: {}", state.getCommParameters());
		// don't check need_plcw because persistence will never trigger plcw
		if (state.getMacQueue().isEmpty() && state.isTxReady()) {
			state.getMacQueue().add(state.getCommParameters());
			state.setState(MacState.S56_COMM_CHANGE);
		}
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
