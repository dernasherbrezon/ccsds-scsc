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

public class FullDuplexMac {

	private final static Logger LOG = LoggerFactory.getLogger(FullDuplexMac.class);

	private final State state;
	private final Configuration config;
	private final Transceiver transceiver;
	private final TransceiverUtil transceiverUtil;
	private final NodeCallback callback;
	private final TimerService timer;

	private final int spduVersion;
	private int currentHailAttempt = 0;
	private int currentCommChangeAttempt = 0;

	public FullDuplexMac(State state, Configuration config, Transceiver transceiver, NodeCallback callback, TimerService timer) {
		this.state = state;
		this.config = config;
		this.transceiver = transceiver;
		this.transceiverUtil = new TransceiverUtil(config, transceiver);
		this.callback = callback;
		this.spduVersion = config.getInteger("spdu_version", 1);
		this.timer = timer;
	}

	public void handle(Event e) {
		handleHail(e);
		handleReconnect(e);
		handleCommChange(e);
		handleTermination(e);
	}

	@SuppressWarnings("incomplete-switch")
	private void handleHail(Event e) {
		switch (e.getType()) {
		case E1_CONNECTING_L:
			LOG.info("waiting for hail");
			state.setState(MacState.S2_WAITING_FOR_HAIL);
			state.setTransceiverMode(TransceiverOperationMode.RESPONDER);
			Spdu hailCommParameters = transceiverUtil.createHailTxRx();
			SpduUtil.setDuplex(hailCommParameters, state.getDuplex());
			transceiverUtil.applyRxParameters(hailCommParameters, state.getTransceiverMode().equals(TransceiverOperationMode.CALLER));
			transceiver.setTxState(TransceiverTxState.OFF);
			transceiver.setRxState(TransceiverRxState.RECEIVING);
			break;
		case E2_CONNECTING_T:
			if (state.getCommParameters() == null) {
				LOG.error("comm parameters not configured");
				callback.handle(config.createNotification("hail", false));
				return;
			}
			if (!SpduUtil.isValidDirective(spduVersion, state.getCommParameters())) {
				LOG.error("mismatch between provided comm parameters and spdu version: {}", spduVersion);
				callback.handle(config.createNotification("hail", false));
				return;
			}
			LOG.info("attempting hail: {} of {}", (currentHailAttempt + 1), config.getInteger("hail_lifetime", 0));
			state.setTransceiverMode(TransceiverOperationMode.CALLER);
			hailCommParameters = transceiverUtil.createHailTxRx();
			SpduUtil.setDuplex(hailCommParameters, state.getDuplex());
			transceiverUtil.applyTxParameters(hailCommParameters, state.getTransceiverMode().equals(TransceiverOperationMode.CALLER));
			transceiver.setTxState(TransceiverTxState.CARRIER_ONLY);
			transceiverUtil.applyRxParameters(state.getCommParameters(), state.getTransceiverMode().equals(TransceiverOperationMode.CALLER));
			transceiver.setRxState(TransceiverRxState.RECEIVING);
			state.setPersistence(true);
			state.setState(MacState.S31_START_HAIL_ACTION);
			timer.scheduleWait(EventType.E4_CARRIER_ONLY_TIMEOUT, config.getDuration("carrier_only_duration"));
			break;
		case E4_CARRIER_ONLY_TIMEOUT:
			if (state.getState().equals(MacState.S31_START_HAIL_ACTION)) {
				transceiver.setTxState(TransceiverTxState.MODULATION);
				state.setState(MacState.S32_SEND_HAIL_ACQUISITION);
				timer.scheduleWait(EventType.E5_ACQUISITION_IDLE_TIMEOUT, config.getDuration("acquisition_idle_duration"));
			}
			break;
		case E5_ACQUISITION_IDLE_TIMEOUT:
			if (state.getState().equals(MacState.S32_SEND_HAIL_ACQUISITION)) {
				state.getMacQueue().add(state.getCommParameters());
				state.setState(MacState.S33_SEND_HAIL_DIRECTIVES);
			}
			break;
		case TX_COMPLETE:
			if (state.getState().equals(MacState.S33_SEND_HAIL_DIRECTIVES)) {
				state.setState(MacState.S34_SEND_HAIL_TAIL);
				timer.scheduleWait(EventType.E7_TAIL_IDLE_TIMEOUT, config.getDuration("tail_idle_duration"));
			}
			break;
		case E7_TAIL_IDLE_TIMEOUT:
			if (state.getState().equals(MacState.S34_SEND_HAIL_TAIL)) {
				state.setState(MacState.S35_WAITING_FOR_HAIL_RESPONSE);
				transceiver.setTxState(TransceiverTxState.OFF);
				timer.scheduleWait(EventType.E8_HAIL_WAIT_TIMEOUT, config.getDuration("hail_wait_duration"));
			}
			break;
		case SPDU:
			Spdu spdu = (Spdu) e.getArg();
			// check several states because responder can send response faster than extra
			// idle and carrier timeouts
			if (state.getState().equals(MacState.S35_WAITING_FOR_HAIL_RESPONSE) || state.getState().equals(MacState.S34_SEND_HAIL_TAIL)) {
				timer.cancelWait();
				callback.handle(config.createNotification("hail", true));
				currentHailAttempt = 0;
				// switch over to the agreed link
				transceiverUtil.applyParameters(state.getCommParameters(), state.getTransceiverMode().equals(TransceiverOperationMode.CALLER));
				state.setState(MacState.S41_RADIATE_CARRIER);
				transceiver.setTxState(TransceiverTxState.CARRIER_ONLY);
				timer.scheduleWait(EventType.E10_CARRIER_ONLY_TIMEOUT, config.getDuration("carrier_only_duration"));
				LOG.info("session established");
			}
			if (state.getState().equals(MacState.S2_WAITING_FOR_HAIL)) {
				if (!SpduUtil.isValidDirective(spduVersion, spdu) || !SpduUtil.isCommChange(spdu)) {
					break;
				}
				state.setCommParameters(spdu);
				// switch over to the agreed link once plcw sent
				// assume
				transceiverUtil.applyParameters(state.getCommParameters(), state.getTransceiverMode().equals(TransceiverOperationMode.CALLER));
				state.setState(MacState.S41_RADIATE_CARRIER);
				transceiver.setTxState(TransceiverTxState.CARRIER_ONLY);
				callback.handle(config.createNotification("hail", true));
				timer.scheduleWait(EventType.E10_CARRIER_ONLY_TIMEOUT, config.getDuration("carrier_only_duration"));
				state.setNeedPlcw(true);
				LOG.info("session established");
			}
			break;
		case E10_CARRIER_ONLY_TIMEOUT:
			if (state.getState().equals(MacState.S41_RADIATE_CARRIER)) {
				state.setState(MacState.S42_RADIATE_ACQUISITION_IDLE);
				timer.scheduleWait(EventType.E11_ACQUISITION_IDLE_TIMEOUT, config.getDuration("acquisition_idle_duration"));
			}
			break;
		case E11_ACQUISITION_IDLE_TIMEOUT:
			if (state.getState().equals(MacState.S42_RADIATE_ACQUISITION_IDLE)) {
				state.setPersistence(false);
				// not according to spec. as soon modulation is set it will output pending
				// frames
				transceiver.setTxState(TransceiverTxState.MODULATION);
				state.setState(MacState.S40_DATA_SERVICES);
				timer.startPeriodicPlcw();
				LOG.info("data services ready");
			}
			break;
		case E8_HAIL_WAIT_TIMEOUT:
			if (state.getState().equals(MacState.S35_WAITING_FOR_HAIL_RESPONSE)) {
				if (state.isCarrierAcquired()) {
					callback.handle(new ControllerEvent(ControllerEventType.CARRIER_ONLY_RECEIVED));
				}
				currentHailAttempt++;
				int maxAttempts = config.getInteger("hail_lifetime", 0);
				if (maxAttempts != 0 && currentHailAttempt >= maxAttempts) {
					currentHailAttempt = 0;
					callback.handle(config.createNotification("hail", false));
				} else {
					sendEvent(new Event(EventType.E2_CONNECTING_T));
				}
			}
			break;
		}
	}

	@SuppressWarnings("incomplete-switch")
	private void handleReconnect(Event e) {
		switch (e.getType()) {
		case CARRIER_LOST:
			// CARRIER_LOSS_TIMER value is ‘0’ and MODE = active;
			if (state.getState().equals(MacState.S40_DATA_SERVICES)) {
				timer.scheduleCarrierLossTimeout(EventType.E80_CARRIER_LOST_TIMEOUT, config.getDuration("carrier_loss_timer_duration"));
			}
			break;
		case E80_CARRIER_LOST_TIMEOUT:
			LOG.info("carrier loss recovery timeout");
			timer.cancelCarrierLoss();
			callback.handle(new ControllerEvent(ControllerEventType.CARRIER_LOSS));
			// stop sending any data, but keep carrier for remote to detect it
			transceiver.setTxState(TransceiverTxState.CARRIER_ONLY);
			timer.scheduleWait(EventType.E81_RECONNECT_WAIT_TIMEOUT, config.getDuration("reconnect_wait_duration"));
			break;
		case E81_RECONNECT_WAIT_TIMEOUT:
			LOG.info("carrier didn't come back. start hailing");
			timer.stopPeriodicPlcw();
			switch (state.getTransceiverMode()) {
			case CALLER:
				sendEvent(new Event(EventType.E2_CONNECTING_T));
				break;
			case RESPONDER:
				sendEvent(new Event(EventType.E1_CONNECTING_L));
				break;
			default:
				LOG.error("unsupported transceiver operating mode: {}", state.getTransceiverMode());
				break;
			}
			break;
		}
	}

	@SuppressWarnings("incomplete-switch")
	private void handleCommChange(Event e) {
		switch (e.getType()) {
		case E12_LOCAL_COMM_CHANGE:
			if (e.getArg() == null) {
				LOG.error("received comm change without payload");
				return;
			}
			state.setCommParameters(SpduUtil.mergeCommParameters(state.getCommParameters(), (Spdu) e.getArg()));
			SpduUtil.setDuplex(state.getCommParameters(), state.getDuplex());
			if (state.getState().equals(MacState.S40_DATA_SERVICES)) {
				sendEvent(new Event(EventType.E14_COMM_CHANGE));
			}
			break;
		case E14_COMM_CHANGE:
			LOG.info("attempting comm_change: {} of {}", (currentCommChangeAttempt + 1), config.getInteger("comm_change_lifetime", 0));
			state.setPersistence(true);
			state.getMacQueue().add(state.getCommParameters());
			state.setState(MacState.S48_2_SEND_COMM_CHANGE);
			break;
		case E18_COMM_CHANGE_WAIT_TIMEOUT:
			currentCommChangeAttempt++;
			int maxAttempts = config.getInteger("comm_change_lifetime", 0);
			if (maxAttempts != 0 && currentCommChangeAttempt >= maxAttempts) {
				currentCommChangeAttempt = 0;
				callback.handle(config.createNotification("comm_change", false));
			} else {
				sendEvent(new Event(EventType.E14_COMM_CHANGE));
			}
			break;
		case E20_COMM_CHANGE_TAIL_TIMEOUT:
			state.setPersistence(false);
			state.setState(MacState.S41_RADIATE_CARRIER);
			transceiver.setTxState(TransceiverTxState.CARRIER_ONLY);
			timer.scheduleWait(EventType.E10_CARRIER_ONLY_TIMEOUT, config.getDuration("carrier_only_duration"));
			transceiverUtil.applyParameters(state.getCommParameters(), state.getTransceiverMode().equals(TransceiverOperationMode.CALLER));
			state.setNeedPlcw(true);
			LOG.info("rx/tx comm parameters changed: {}", state.getCommParameters());
			break;
		case SYMBOL_UNLOCK:
			// comm change routine
			if (state.getState().equals(MacState.S48_2_SEND_COMM_CHANGE) || state.getState().equals(MacState.S48_3_COMM_CHANGE_WAIT)) {
				LOG.info("rx comm parameters changed: {}", state.getCommParameters());
				transceiverUtil.applyRxParameters(state.getCommParameters(), state.getTransceiverMode().equals(TransceiverOperationMode.CALLER));
				state.setState(MacState.S48__1_RX_COMM_CHANGE);
			}
			break;
		case TX_COMPLETE:
			if (state.getState().equals(MacState.S48_2_SEND_COMM_CHANGE)) {
				state.setState(MacState.S48_3_COMM_CHANGE_WAIT);
				timer.scheduleWait(EventType.E18_COMM_CHANGE_WAIT_TIMEOUT, config.getDuration("comm_change_waiting_period"));
			}
			if (state.getState().equals(MacState.S48_4_REMOTE_COMM_CHANGE)) {
				state.setState(MacState.S48_5_COMM_CHANGE_TAIL);
				timer.scheduleWait(EventType.E20_COMM_CHANGE_TAIL_TIMEOUT, config.getDuration("tail_idle_duration"));
			}
			break;
		case SPDU:
			Spdu spdu = (Spdu) e.getArg();
			if (state.getState().equals(MacState.S48__1_RX_COMM_CHANGE)) {
				timer.cancelWait();
				state.setPersistence(false);
				transceiverUtil.applyTxParameters(state.getCommParameters(), state.getTransceiverMode().equals(TransceiverOperationMode.CALLER));
				currentCommChangeAttempt = 0;
				transceiver.setTxState(TransceiverTxState.CARRIER_ONLY);
				state.setState(MacState.S41_RADIATE_CARRIER);
				timer.scheduleWait(EventType.E10_CARRIER_ONLY_TIMEOUT, config.getDuration("carrier_only_duration"));
				LOG.info("tx comm parameters changed: {}", state.getCommParameters());
				callback.handle(config.createNotification("comm_change", true));
			}
			if (state.getState().equals(MacState.S40_DATA_SERVICES)) {
				if (SpduUtil.isCommChange(spdu)) {
					LOG.info("remote comm change parameters requested: {}", spdu);
					state.setPersistence(true);
					state.setCommParameters(SpduUtil.mergeCommParameters(state.getCommParameters(), spdu));
					// FIXME switch mode if changed
					state.setDuplex(SpduUtil.getDuplex(state.getCommParameters()));
					if (state.noSpduPending() && state.isTxReady()) {
						state.setState(MacState.S48_5_COMM_CHANGE_TAIL);
						timer.scheduleWait(EventType.E20_COMM_CHANGE_TAIL_TIMEOUT, config.getDuration("tail_idle_duration"));
					} else {
						state.setState(MacState.S48_4_REMOTE_COMM_CHANGE);
					}
				}
			}
			break;
		}

	}

	@SuppressWarnings("incomplete-switch")
	private void handleTermination(Event e) {
		switch (e.getType()) {
		case E21_LNMD:
			// initiated locally
			if (state.getState().equals(MacState.S40_DATA_SERVICES) && state.getX() == 0) {
				LOG.info("session is stopping");
				state.setX(2);
				Spdu rnmdDirective = SpduUtil.createRnmdDirective(state.getCommParameters(), true);
				if (rnmdDirective == null) {
					LOG.error("unable to create rnmd - not enough data in comm parameters");
					return;
				}
				state.getMacQueue().add(rnmdDirective);
			}
			if (state.getState().equals(MacState.S40_DATA_SERVICES) && state.getX() == 4) {
				state.setX(5);
				Spdu rnmdDirective = SpduUtil.createRnmdDirective(state.getCommParameters(), true);
				if (rnmdDirective == null) {
					LOG.error("unable to create rnmd - not enough data in comm parameters");
					return;
				}
				state.getMacQueue().add(rnmdDirective);
			}
			break;
		case E26_TAIL_IDLE_TIMEOUT:
			if (state.getState().equals(MacState.S45_TERMINATING_TAIL)) {
				LOG.info("session stopped");
				sendEvent(new Event(EventType.E28_INACTIVE));
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
			// initiated remotely
			if (state.getState().equals(MacState.S40_DATA_SERVICES) && state.getX() == 0) {
				LOG.info("session is stopping");
				state.setX(4);
			}
			if (state.getState().equals(MacState.S40_DATA_SERVICES) && state.getX() == 2) {
				state.setX(5);
				// no more frames, transition to the terminating
				if (state.noFramesPending() && state.isTxReady()) {
					state.setState(MacState.S45_TERMINATING_TAIL);
					timer.scheduleWait(EventType.E26_TAIL_IDLE_TIMEOUT, config.getDuration("tail_idle_duration"));
				}
			}
			break;
		case TX_COMPLETE:
			if (state.getState().equals(MacState.S40_DATA_SERVICES) && state.getX() == 5 && state.noFramesPending()) {
				state.setState(MacState.S45_TERMINATING_TAIL);
				timer.scheduleWait(EventType.E26_TAIL_IDLE_TIMEOUT, config.getDuration("tail_idle_duration"));
			}
			break;
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
