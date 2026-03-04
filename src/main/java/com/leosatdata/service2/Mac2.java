package com.leosatdata.service2;

import java.util.Timer;
import java.util.TimerTask;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.leosatdata.model.ControllerEvent;
import com.leosatdata.model.ControllerEventType;
import com.leosatdata.model.Event;
import com.leosatdata.model.EventType;
import com.leosatdata.model.MacMode;
import com.leosatdata.model.PersistentActivity;
import com.leosatdata.model.State;
import com.leosatdata.model.Type1Directive;
import com.leosatdata.model.Type4Directive;
import com.leosatdata.model.Type5Directive;
import com.leosatdata.service.NodeCallback;
import com.leosatdata.service.Transceiver;
import com.leosatdata.util.Configuration;
import com.leosatdata.util.TransceiverUtil;

public class Mac2 {

	private final static Logger LOG = LoggerFactory.getLogger(Mac2.class);
	private final static int HAIL_RESPONSE_FRAME = 0;
	private final static int HAIL_RESPONSE_SYMBOL_IN_LOCK = 1;

	private final State state;
	private final Configuration config;
	private final Transceiver transceiver;
	private final NodeCallback callback;

	private Timer waitTimer;
	private TimerTask waitTimerTask;
	private int currentHailAttempt = 0;
	private boolean hailCarrierReceived;
	private boolean symbolInLock;

	public Mac2(State state, Configuration config, Transceiver transceiver, NodeCallback callback) {
		this.state = state;
		this.config = config;
		this.transceiver = transceiver;
		this.callback = callback;
	}

	public void start() {
		waitTimer = new Timer("wt"); // this will start Thread
	}

	public void handle(Event e) {
		switch (e.getType()) {
		case E2_CONNECTING_T:
			state.setPersistence(true);
			LOG.info("attempting hail: {} of {}", currentHailAttempt, config.getInteger("hail_lifetime", 0));
			state.setMode(MacMode.CONNECTING_T);
			String frameVersion = config.getProperty("frame_version");
			if (frameVersion.equals("3")) {
				TransceiverUtil.applyProximityParameters(TransceiverUtil.createHailProximityTxRx(config), config, transceiver);
			} else if (frameVersion.equals("4")) {
				TransceiverUtil.applyUslpParameters(TransceiverUtil.createHailUslpTxRx(config), transceiver);
			} else {
				throw new IllegalArgumentException("unsupported frame_version: " + frameVersion);
			}
			transceiver.startCarrier();
			scheduleWait(EventType.E4_CARRIER_ONLY_TIMEOUT, config.getDuration("carrier_only_duration"));
			break;
		case E4_CARRIER_ONLY_TIMEOUT:
			transceiver.startIdle();
			scheduleWait(EventType.E5_ACQUISITION_IDLE_TIMEOUT, config.getDuration("acquisition_idle_duration"));
			break;
		case E5_ACQUISITION_IDLE_TIMEOUT:
			// FIXME most likely need an event to trigger queue check
//			state.getMacQueue().add(state.getCurrentLink());
			break;
		case TX_COMPLETE:
			// FIXME check current state. if hail, then schedule timeout, if normal/active
			// then take next from the queue
//			scheduleWait(EventType.E7_TAIL_IDLE_TIMEOUT, config.getDuration("tail_idle_duration"));
			break;
		case E7_TAIL_IDLE_TIMEOUT:
			hailCarrierReceived = false;
			transceiver.startRx();
			scheduleWait(EventType.E8_HAIL_WAIT_TIMEOUT, config.getDuration("hail_wait_duration"));
			break;
		case E8_1_CARRIER_RECEIVED:
			hailCarrierReceived = true;
			break;
		case E8_2_SYMBOL_IN_LOCK:
			symbolInLock = true;
			if (config.getInteger("hail_response", 0) != HAIL_RESPONSE_SYMBOL_IN_LOCK) {
				break;
			}
			// no break clause here because should proceed as valid frame
		case SPDU:
			waitTimerTask.cancel();
			callback.handle(createNotification("hail", true));
			currentHailAttempt = 0;
			// switch over to the agreed link
			TransceiverUtil.applyParameters(state.getCurrentLink(), config, transceiver);
			transceiver.startCarrier();
			scheduleWait(EventType.E10_CARRIER_ONLY_TIMEOUT, config.getDuration("carrier_only_duration"));
			break;
		case E10_CARRIER_ONLY_TIMEOUT:
			transceiver.startIdle();
			scheduleWait(EventType.E11_ACQUISITION_IDLE_TIMEOUT, config.getDuration("acquisition_idle_duration"));
			break;
		case E11_ACQUISITION_IDLE_TIMEOUT:
			state.setPersistence(false);
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
				return;
			}
			sendEvent(new Event(EventType.E2_CONNECTING_T));
			break;

		case E12_LOCAL_COMM_CHANGE:
			if (e.getArg() == null) {
				LOG.error("received comm change without payload");
				return;
			}
			if (e.getArg() instanceof Type1Directive) {
				Type1Directive obj = (Type1Directive) e.getArg();
				if (state.getCurrentLink() == null) {
					state.setCurrentLink(obj);
				} else {
					((Type1Directive) state.getCurrentLink()).merge(obj);
				}
			} else if (e.getArg() instanceof Type4Directive) {
				Type4Directive obj = (Type4Directive) e.getArg();
				if (state.getCurrentLink() == null) {
					state.setCurrentLink(obj);
				} else {
					((Type4Directive) state.getCurrentLink()).merge(obj);
				}
			} else if (e.getArg() instanceof Type5Directive) {
				Type5Directive obj = (Type5Directive) e.getArg();
				if (state.getCurrentLink() == null) {
					state.setCurrentLink(obj);
				} else {
					((Type5Directive) state.getCurrentLink()).merge(obj);
				}
			} else {
				LOG.error("unknown arg type: {}", e.getArg());
				return;
			}
			if (state.getMode().equals(MacMode.ACTIVE)) {
				// FIXME send remote change
			}
			break;
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
		waitTimer.schedule(waitTimerTask, duration);
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
