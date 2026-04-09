package com.leosatdata.service;

import java.util.Timer;
import java.util.TimerTask;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.leosatdata.model.ControlParameters;
import com.leosatdata.model.ControllerEvent;
import com.leosatdata.model.ControllerEventType;
import com.leosatdata.model.Duplex;
import com.leosatdata.model.Event;
import com.leosatdata.model.EventType;
import com.leosatdata.model.MacState;
import com.leosatdata.model.PersistentActivity;
import com.leosatdata.model.Plcw;
import com.leosatdata.model.Spdu;
import com.leosatdata.model.State;
import com.leosatdata.model.TransceiverOperationMode;
import com.leosatdata.model.Type1Directive;
import com.leosatdata.model.Type4Directive;
import com.leosatdata.model.Type5Directive;
import com.leosatdata.util.Configuration;
import com.leosatdata.util.SpduUtil;
import com.leosatdata.util.TransceiverUtil;

public class Mac {

	private final static Logger LOG = LoggerFactory.getLogger(Mac.class);
	private final static int HAIL_RESPONSE_FRAME = 0;
	private final static int HAIL_RESPONSE_SYMBOL_IN_LOCK = 1;

	private final State state;
	private final Configuration config;
	private final Transceiver transceiver;
	private final TransceiverUtil transceiverUtil;
	private final NodeCallback callback;
	private final int spduVersion;

	private Timer waitTimer;
	private TimerTask waitTimerTask;
	private TimerTask carrierLossTask;
	private int currentHailAttempt = 0;
	private int currentCommChangeAttempt = 0;
	private boolean hailCarrierReceived;
	private boolean symbolInLock;
	private TransceiverOperationMode transceiverMode = TransceiverOperationMode.RESPONDER; // by default passive

	public Mac(State state, Configuration config, Transceiver transceiver, NodeCallback callback) {
		this.state = state;
		this.config = config;
		this.transceiver = transceiver;
		this.transceiverUtil = new TransceiverUtil(config, transceiver);
		this.callback = callback;
		this.spduVersion = config.getInteger("spdu_version", 1);
	}

	public void start() {
		waitTimer = new Timer("wt"); // this will start Thread
	}

	public void stop() {
		if (waitTimer != null) {
			waitTimer.cancel();
		}
	}

	public void handle(Event e) {
		handleHail(e);
		handleTermination(e);
	}

	@SuppressWarnings("incomplete-switch")
	private void handleHail(Event e) {
		switch (e.getType()) {
		case E1_CONNECTING_L:
			LOG.info("waiting for hail");
			state.setState(MacState.S2_WAITING_FOR_HAIL_REQUEST);
			transceiverMode = TransceiverOperationMode.RESPONDER;
			transceiverUtil.applyParameters(transceiverUtil.createHailTxRx());
			transceiver.startTx();
			transceiver.startRx();
			state.setTransmit(true);
			break;
		case E2_CONNECTING_T:
			if (state.getCommParameters() == null) {
				LOG.error("comm parameters not configured");
				callback.handle(createNotification("hail", false));
				return;
			}
			if (!validateSpdu(state.getCommParameters())) {
				LOG.error("mismatch between provided comm parameters and spdu version: {}", spduVersion);
				callback.handle(createNotification("hail", false));
				return;
			}
			LOG.info("attempting hail: {} of {}", (currentHailAttempt + 1), config.getInteger("hail_lifetime", 0));
			transceiverMode = TransceiverOperationMode.CALLER;
			transceiverUtil.applyParameters(transceiverUtil.createHailTxRx());
			transceiver.startTx();
			transceiver.startRx();
			state.setPersistence(true);
			state.setTransmit(true);
			state.setState(MacState.S31_START_HAIL);
			scheduleWait(EventType.E4_CARRIER_ONLY_TIMEOUT, config.getDuration("carrier_only_duration"));
			break;
		case E4_CARRIER_ONLY_TIMEOUT:
			transceiver.startIdle();
			state.setState(MacState.S32_SEND_HAIL_ACQUISITION);
			scheduleWait(EventType.E5_ACQUISITION_IDLE_TIMEOUT, config.getDuration("acquisition_idle_duration"));
			break;
		case E5_ACQUISITION_IDLE_TIMEOUT:
			state.getMacQueue().add(state.getCommParameters());
			state.setState(MacState.S33_SEND_HAIL_DIRECTIVES);
			break;
		case TX_COMPLETE:
			if (state.getState().equals(MacState.S33_SEND_HAIL_DIRECTIVES)) {
				state.setState(MacState.S34_SEND_HAIL_TAIL);
				scheduleWait(EventType.E7_TAIL_IDLE_TIMEOUT, config.getDuration("tail_idle_duration"));
			}
			if (state.getState().equals(MacState.S2_WAITING_FOR_HAIL_REQUEST)) {
				state.setState(MacState.S41_RADIATE_CARRIER);
				// switch over to the agreed link once plcw sent
				transceiverUtil.applyParameters(state.getCommParameters());
				callback.handle(createNotification("hail", true));
				scheduleWait(EventType.E10_CARRIER_ONLY_TIMEOUT, config.getDuration("carrier_only_duration"));
			}
			if (state.getState().equals(MacState.S48_2_SEND_COMM_CHANGE)) {
				state.setState(MacState.S48_3_COMM_CHANGE_WAIT);
				scheduleWait(EventType.E18_COMM_CHANGE_WAIT_TIMEOUT, config.getDuration("comm_change_waiting_period"));
			}
			if (state.getState().equals(MacState.S48_4_REMOTE_COMM_CHANGE)) {
				state.setState(MacState.S48_5_COMM_CHANGE_TAIL);
				scheduleWait(EventType.E20_COMM_CHANGE_TAIL_TIMEOUT, config.getDuration("tail_idle_duration"));
			}
			break;
		case E7_TAIL_IDLE_TIMEOUT:
			hailCarrierReceived = false;
			state.setState(MacState.S35_WAITING_FOR_HAIL_RESPONSE);
			scheduleWait(EventType.E8_HAIL_WAIT_TIMEOUT, config.getDuration("hail_wait_duration"));
			break;
		case E8_1_CARRIER_RECEIVED:
			if (state.getState().equals(MacState.S35_WAITING_FOR_HAIL_RESPONSE)) {
				hailCarrierReceived = true;
			}
			if (carrierLossTask != null) {
				LOG.info("carrier recovered");
				carrierLossTask.cancel();
				carrierLossTask = null;
			}
			break;
		case E8_1_CARRIER_LOST:
			// CARRIER_LOSS_TIMER value is ‘0’ and MODE = active;
			LOG.info("carrier lost state is: {}", state.getState());
			if (carrierLossTask == null && state.getState().equals(MacState.S40_0_DATA_SERVICES)) {
				scheduleCarrierLossTimeout(EventType.E80_CARRIER_LOST_TIMEOUT, config.getDuration("carrier_loss_timer_duration"));
			}
			// comm change routine
			if (state.getState().equals(MacState.S48_2_SEND_COMM_CHANGE) || state.getState().equals(MacState.S48_3_COMM_CHANGE_WAIT)) {
				transceiverUtil.applyRxParameters(state.getCommParameters());
				state.setState(MacState.S48__1_RX_COMM_CHANGE);
			}
			break;
		case E80_CARRIER_LOST_TIMEOUT:
			LOG.info("carrier loss recovery timeout");
			callback.handle(new ControllerEvent(ControllerEventType.CARRIER_LOSS));
			state.setTransmit(false);
			scheduleWait(EventType.E81_RECONNECT_WAIT_TIMEOUT, config.getDuration("reconnect_wait_duration"));
			break;
		case E81_RECONNECT_WAIT_TIMEOUT:
			switch (transceiverMode) {
			case CALLER:
				sendEvent(new Event(EventType.E2_CONNECTING_T));
				break;
			case RESPONDER:
				sendEvent(new Event(EventType.E1_CONNECTING_L));
				break;
			default:
				LOG.error("unsupported transceiver operating mode: {}", transceiverMode);
				break;
			}
			break;
		case E8_2_SYMBOL_IN_LOCK:
			symbolInLock = true;
			if (config.getInteger("hail_response", 0) != HAIL_RESPONSE_SYMBOL_IN_LOCK) {
				break;
			}
			// no break clause here because should proceed as valid frame
		case SPDU:
			// check several states because responder can send response faster than extra
			// idle and carrier timeouts
			if (state.getState().equals(MacState.S35_WAITING_FOR_HAIL_RESPONSE) || state.getState().equals(MacState.S34_SEND_HAIL_TAIL)) {
				waitTimerTask.cancel();
				callback.handle(createNotification("hail", true));
				currentHailAttempt = 0;
				// switch over to the agreed link
				transceiverUtil.applyParameters(state.getCommParameters());
				state.setState(MacState.S41_RADIATE_CARRIER);
				scheduleWait(EventType.E10_CARRIER_ONLY_TIMEOUT, config.getDuration("carrier_only_duration"));
				LOG.info("session established");
			}
			if (state.getState().equals(MacState.S2_WAITING_FOR_HAIL_REQUEST)) {
				if (!validateSpdu((Spdu) e.getArg())) {
					break;
				}
				state.setCommParameters((Spdu) e.getArg());
				state.setNeedPlcw(true);
			}
			if (state.getState().equals(MacState.S48__1_RX_COMM_CHANGE)) {
				waitTimerTask.cancel();
				callback.handle(createNotification("comm_change", true));
				state.setPersistence(false);
				transceiverUtil.applyTxParameters(state.getCommParameters());
				currentCommChangeAttempt = 0;
				state.setState(MacState.S41_RADIATE_CARRIER);
				scheduleWait(EventType.E10_CARRIER_ONLY_TIMEOUT, config.getDuration("carrier_only_duration"));
			}
			if (state.getState().equals(MacState.S40_0_DATA_SERVICES)) {
				if (SpduUtil.isCommChange((Spdu) e.getArg())) {
					LOG.info("remote comm change parameters requested: {}", e.getArg());
					state.setPersistence(true);
					SpduUtil.mergeCommParameters(state.getCommParameters(), (Spdu) e.getArg());
					// no pending frames on transceiver
					if (state.isTxReady()) {
						state.setState(MacState.S48_5_COMM_CHANGE_TAIL);
						scheduleWait(EventType.E20_COMM_CHANGE_TAIL_TIMEOUT, config.getDuration("tail_idle_duration"));
					} else {
						state.setState(MacState.S48_4_REMOTE_COMM_CHANGE);
					}
				}
			}
			break;
		case E10_CARRIER_ONLY_TIMEOUT:
			transceiver.startIdle();
			state.setState(MacState.S42_RADIATE_ACQUISITION_IDLE);
			scheduleWait(EventType.E11_ACQUISITION_IDLE_TIMEOUT, config.getDuration("acquisition_idle_duration"));
			break;
		case E11_ACQUISITION_IDLE_TIMEOUT:
			state.setPersistence(false);
			state.setState(MacState.S40_0_DATA_SERVICES);
			LOG.info("data services ready");
			break;
		case E8_HAIL_WAIT_TIMEOUT:
			if (hailCarrierReceived) {
				callback.handle(new ControllerEvent(ControllerEventType.CARRIER_ONLY_RECEIVED));
			}
			currentHailAttempt++;
			int maxAttempts = config.getInteger("hail_lifetime", 0);
			if (maxAttempts != 0 && currentHailAttempt >= maxAttempts) {
				currentHailAttempt = 0;
				callback.handle(createNotification("hail", false));
			} else {
				sendEvent(new Event(EventType.E2_CONNECTING_T));
			}
			break;
		case E12_LOCAL_COMM_CHANGE:
			if (e.getArg() == null) {
				LOG.error("received comm change without payload");
				return;
			}
			// FIXME move to mergeCommParameters
			if (e.getArg() instanceof Type1Directive) {
				// FIXME default duplex mode
				ControlParameters controlParameters = new ControlParameters();
				controlParameters.setDuplex(Duplex.FULL_DUPLEX);
				controlParameters.setRnmd(false);
				controlParameters.setTimeSampleField(0);
				controlParameters.setTransmitToken(false);
				Type1Directive obj = (Type1Directive) e.getArg();
				obj.setControlParams(controlParameters);
				if (state.getCommParameters() == null) {
					state.setCommParameters(obj);
				} else {
					((Type1Directive) state.getCommParameters()).merge(obj);
				}
			} else if (e.getArg() instanceof Type4Directive) {
				Type4Directive obj = (Type4Directive) e.getArg();
				if (state.getCommParameters() == null) {
					state.setCommParameters(obj);
				} else {
					((Type4Directive) state.getCommParameters()).merge(obj);
				}
			} else if (e.getArg() instanceof Type5Directive) {
				Type5Directive obj = (Type5Directive) e.getArg();
				if (state.getCommParameters() == null) {
					state.setCommParameters(obj);
				} else {
					((Type5Directive) state.getCommParameters()).merge(obj);
				}
			} else {
				LOG.error("unknown arg type: {}", e.getArg());
				return;
			}
			if (state.getState().equals(MacState.S40_0_DATA_SERVICES)) {
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
			maxAttempts = config.getInteger("comm_change_lifetime", 0);
			if (maxAttempts != 0 && currentCommChangeAttempt >= maxAttempts) {
				currentCommChangeAttempt = 0;
				callback.handle(createNotification("comm_change", false));
			} else {
				sendEvent(new Event(EventType.E14_COMM_CHANGE));
			}
			break;
		case E20_COMM_CHANGE_TAIL_TIMEOUT:
			state.setPersistence(false);
			state.setState(MacState.S41_RADIATE_CARRIER);
			scheduleWait(EventType.E10_CARRIER_ONLY_TIMEOUT, config.getDuration("carrier_only_duration"));
			transceiverUtil.applyParameters(state.getCommParameters());
			state.setNeedPlcw(true);
			LOG.info("comm parameters changed: {}", state.getCommParameters());
			break;
		}
	}

	// when initiated locally (lnmd) send to remote and wait response rnmd, then
	// terminate
	// when initiated remotely (rnmd) send response, wait until it was fully sent,
	// then terminate
	@SuppressWarnings("incomplete-switch")
	private void handleTermination(Event e) {
		switch (e.getType()) {
		case E21_LNMD:
			if (state.getState().equals(MacState.S40_0_DATA_SERVICES)) {
				LOG.info("session is stopping");
				state.setState(MacState.S40_2_DATA_SERVICES);
				Spdu rnmdDirective = SpduUtil.createRnmdDirective(state.getCommParameters(), true);
				if (rnmdDirective == null) {
					LOG.error("unable to create rnmd - not enough data in comm parameters");
					return;
				}
				state.getMacQueue().add(rnmdDirective);
			}
			break;
		case E26_TAIL_IDLE_TIMEOUT:
			LOG.info("session stopped");
			state.setState(MacState.S1_INACTIVE);
			// don't send number of received packets. not clear why and hard to sync rx
			// thread data
			callback.handle(new ControllerEvent(ControllerEventType.END_OF_SESSION));
			break;
		case SPDU:
			if (e.getArg() instanceof Plcw) {
				break;
			}
			boolean rnmd = SpduUtil.isRnmd((Spdu) e.getArg());
			if (!rnmd) {
				break;
			}
			if (state.getState().equals(MacState.S40_0_DATA_SERVICES)) {
				LOG.info("session is stopping");
				state.setState(MacState.S40_5_DATA_SERVICES);
				Spdu rnmdDirective = SpduUtil.createRnmdDirective(state.getCommParameters(), true);
				if (rnmdDirective == null) {
					LOG.error("unable to create rnmd - not enough data in comm parameters");
					return;
				}
				state.getMacQueue().add(rnmdDirective);
			}
			if (state.getState().equals(MacState.S40_2_DATA_SERVICES)) {
				state.setState(MacState.S40_5_DATA_SERVICES);
				scheduleWait(EventType.E26_TAIL_IDLE_TIMEOUT, config.getDuration("tail_idle_duration"));
			}
			break;
		case TX_COMPLETE:
			if (state.getState().equals(MacState.S40_5_DATA_SERVICES)) {
				scheduleWait(EventType.E26_TAIL_IDLE_TIMEOUT, config.getDuration("tail_idle_duration"));
			}
			break;
		}
	}

	private boolean validateSpdu(Spdu obj) {
		if (obj == null) {
			return false;
		}
		if (obj instanceof Type1Directive) {
			return spduVersion == Type1Directive.SPDU_VERSION;
		} else if (obj instanceof Type4Directive) {
			return spduVersion == Type4Directive.SPDU_VERSION;
		} else if (obj instanceof Type5Directive) {
			return spduVersion == Type5Directive.SPDU_VERSION;
		} else {
			return false;
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

	private void scheduleWait(EventType type, long duration) {
		waitTimerTask = new TimerTask() {

			@Override
			public void run() {
				sendEvent(new Event(type));
			}
		};
		LOG.info("waiting: {} for {} ms", type, duration);
		waitTimer.schedule(waitTimerTask, duration);
	}

	private void scheduleCarrierLossTimeout(EventType type, long duration) {
		carrierLossTask = new TimerTask() {

			@Override
			public void run() {
				sendEvent(new Event(type));
			}
		};
		LOG.info("carrier loss: {} for {} ms", type, duration);
		waitTimer.schedule(carrierLossTask, duration);
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
