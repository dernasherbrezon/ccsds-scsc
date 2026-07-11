package com.leosatdata.service;

import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.leosatdata.ccsds2110b6.model.PduTypeId;
import com.leosatdata.ccsds2110b6.model.QualityOfService;
import com.leosatdata.ccsds2110b6.model.SourceDestination;
import com.leosatdata.model.ControllerEvent;
import com.leosatdata.model.ControllerEventType;
import com.leosatdata.model.Duplex;
import com.leosatdata.model.ErrorCode;
import com.leosatdata.model.Event;
import com.leosatdata.model.EventType;
import com.leosatdata.model.MacState;
import com.leosatdata.model.QueueType;
import com.leosatdata.model.Spdu;
import com.leosatdata.model.State;
import com.leosatdata.model.TimeDelayCalculationResult;
import com.leosatdata.model.TimeDistribution;
import com.leosatdata.model.TimeDistributionType;
import com.leosatdata.model.TimeTag;
import com.leosatdata.model.TimeTagType;
import com.leosatdata.util.Configuration;
import com.leosatdata.util.SpduUtil;
import com.leosatdata.util.TransferFrameUtil;
import com.leosatdata.util.Util;

public class Node implements Runnable, TransceiverCallback {

	private final static Logger LOG = LoggerFactory.getLogger(Node.class);
	private final static Logger STATE_LOG = LoggerFactory.getLogger("state");

	private final Configuration config;
	private final Transceiver transceiver;
	private final NodeCallback callback;
	private final UserDataCallback rxCallback;
	private final State state;
	private final FullDuplexMac fullDuplexMac;
	private final HalfDuplexMac halfDuplexMac;
	private final SimplexMac simplexMac;
	private final FrameSublayer frameSublayer;
	private final TimerService timer;

	private final Thread t;
	private final int localScid;
	private final boolean testSource;
	private final int maxSequenceNumber;
	private int remoteScid;
	// there are 2 vr variables. One is thread-local onRx, the other one is in State
	// this is done to synchronize the state across 2 threads
	private int vrFromCallback = 0;
	// this variable is visible on transceiver rx thread only
	// and shared between onAsmRx and onRx callbacks
	private long rxAsmTime = 0;
	private TimeDelayCalculationResult timeDelayCalculation;

	public Node(Configuration config, Transceiver transceiver, NodeCallback callback, UserDataCallback rxCallback) {
		this.config = config;
		this.transceiver = transceiver;
		this.transceiver.setCallback(this);
		this.callback = callback;
		this.rxCallback = rxCallback;
		this.localScid = config.getInteger("local_spacecraft_id", 0);
		this.testSource = config.getBoolean("test_source");
		this.remoteScid = config.getInteger("remote_spacecraft_id", -1);
		this.maxSequenceNumber = (config.getInteger("maximum_frame_sequence_length", 0) == 1) ? 0xFF : 0xFFFF;
		this.state = new State(config);
		this.state.setDuplex(Duplex.FULL_DUPLEX); // reasonable default
		this.timer = new TimerService(state, config);
		this.fullDuplexMac = new FullDuplexMac(state, config, transceiver, callback, timer);
		this.halfDuplexMac = new HalfDuplexMac(state, config, transceiver, callback, timer);
		this.simplexMac = new SimplexMac(state, config, transceiver, callback, timer);
		this.frameSublayer = new FrameSublayer(state, config, transceiver, callback);
		t = new Thread(this, "node-" + localScid);
		t.setDaemon(true);
	}

	@SuppressWarnings("incomplete-switch")
	private void handle(Event e) {
		switch (e.getType()) {
		case E28_INACTIVE:
			// reset remote spacecraft id if it was discovered
			if (config.getInteger("remote_spacecraft_id", -1) == -1 && remoteScid != -1) {
				remoteScid = -1;
			}
			timer.cancelWait();
			timer.cancelCarrierLoss();
			state.reset();
			transceiver.setTxState(TransceiverTxState.OFF);
			transceiver.setRxState(TransceiverRxState.OFF);
			transceiver.enableAsmRxCallback(false);
			transceiver.enableAsmTxCallback(false);
			timeDelayCalculation = null;
			// don't send number of received packets. not clear why and hard to sync rx
			// thread data
			callback.handle(new ControllerEvent(ControllerEventType.END_OF_SESSION));
			return;
		case DUPLEX:
			state.setDuplex((Duplex) e.getArg());
			if (state.getCommParameters() != null) {
				SpduUtil.setDuplex(state.getCommParameters(), state.getDuplex());
			}
			return;
		case CARRIER_ACQUIRED:
			LOG.debug("carrier acquired");
			state.setCarrierAcquired(true);
			timer.cancelCarrierLoss();
			break;
		case CARRIER_LOST:
			LOG.debug("carrier lost");
			state.setCarrierAcquired(false);
			break;
		case REQUEST_PLCW:
			state.setNeedPlcw(true);
			break;
		case TIME_DISTRIBUTION:
			if (!isDataServices()) {
				callback.handle(new ControllerEvent(ControllerEventType.TIME_DISTRIBUTION, 1, "not in data services state"));
				return;
			}
			state.getMacQueue().add((Spdu) e.getArg());
			break;
		case TIME_TAGGING:
			if (!isDataServices() || isTimeTaggingActive()) {
				callback.handle(new ControllerEvent(ControllerEventType.TIMING_SERVICES_INSTANCE, 1, "not in data services state"));
				return;
			}
			int numberOfFramesToTag = (int) e.getArg();
			startTimeTaggingInternal(numberOfFramesToTag);
			state.getMacQueue().add(frameSublayer.createTimeTagDirective(numberOfFramesToTag));
			break;
		case TIME_DELAY_CALCULATION:
			if (!isDataServices() || isTimeTaggingActive()) {
				callback.handle(new ControllerEvent(ControllerEventType.TIME_DELAY_CALCULATION, 1, "not in data services state"));
				return;
			}
			timeDelayCalculation = new TimeDelayCalculationResult();
			timeDelayCalculation.setInitialTimeMillis((long) e.getArg());

			transceiver.enableAsmRxCallback(true);
			transceiver.enableAsmTxCallback(true);
			// this will take priority over any other frame types
			// extra out-of-order plcw won't cause any harm
			state.getMacQueue().add(frameSublayer.createPlcw());
			break;
		case SPDU:
			Spdu spdu = (Spdu) e.getArg();
			if (spdu instanceof TimeDistribution) {
				callback.handle(new ControllerEvent(ControllerEventType.TIME_DISTRIBUTION, spdu));
				return;
			}
			numberOfFramesToTag = SpduUtil.getTimeSample(spdu);
			if (numberOfFramesToTag != 0) {
				// safety
				if (!isDataServices()) {
					LOG.error("non data service state. can't start time tagging");
					break;
				}
				startTimeTaggingInternal(numberOfFramesToTag);
			}
			break;
		case ASM_RX:
			// snap rx time only once
			if (timeDelayCalculation != null && timeDelayCalculation.getRxAsmTimeMillis() == 0) {
				transceiver.enableAsmRxCallback(false);
				timeDelayCalculation.setRxAsmTimeMillis(((TimeTag) e.getArg()).getTime());
				if (timeDelayCalculation.isComplete()) {
					callback.handle(new ControllerEvent(ControllerEventType.TIME_DELAY_CALCULATION, timeDelayCalculation));
					timeDelayCalculation = null;
				}
				return;
			}
			if (state.getTags() != null) {
				// receive whole TimeTag instance here
				// this is done to minimize time tagging affect on performance
				// + share variable between TX and RX threads
				state.setRxFramesToTimeTag(state.getRxFramesToTimeTag() - 1);
				if (state.getRxFramesToTimeTag() < 0) {
					transceiver.enableAsmRxCallback(false);
				} else {
					state.getTags().add((TimeTag) e.getArg());
				}
				if (state.getRxFramesToTimeTag() < 0 && state.getTxFramesToTimeTag() < 0) {
					callback.handle(new ControllerEvent(ControllerEventType.TIMING_SERVICES_INSTANCE, state.getTags()));
					state.setTags(null);
				}
			}
			return;
		case ASM_TX:
			// time calibration
			// snap tx time only once
			if (timeDelayCalculation != null && timeDelayCalculation.getTxAsmTimeMillis() == 0) {
				// ignore late callbacks interested only in the very first
				transceiver.enableAsmTxCallback(false);
				timeDelayCalculation.setTxAsmTimeMillis((long) e.getArg());
				if (timeDelayCalculation.isComplete()) {
					callback.handle(new ControllerEvent(ControllerEventType.TIME_DELAY_CALCULATION, timeDelayCalculation));
					timeDelayCalculation = null;
				}
				return;
			}
			// time tagging
			if (state.getTags() != null) {
				state.setTxFramesToTimeTag(state.getTxFramesToTimeTag() - 1);
				if (state.getTxFramesToTimeTag() < 0) {
					transceiver.enableAsmTxCallback(false);
				} else {
					state.getTags().add(new TimeTag((long) e.getArg(), state.getCurrentSequenceNumber(), state.getCurrentQos(), TimeTagType.TX));
				}
				if (state.getRxFramesToTimeTag() < 0 && state.getTxFramesToTimeTag() < 0) {
					callback.handle(new ControllerEvent(ControllerEventType.TIMING_SERVICES_INSTANCE, state.getTags()));
					state.setTags(null);
				}
			}
			return;
// the below TODO can be replaced by hashmap
		case CONNECTING_L:
			if (state.getDuplex().equals(Duplex.FULL_DUPLEX)) {
				e = new Event(EventType.E1_CONNECTING_L);
			} else if (state.getDuplex().equals(Duplex.HALF_DUPLEX)) {
				e = new Event(EventType.E29_CONNECTING_L);
			} else if (state.getDuplex().equals(Duplex.SIMPLEX_RECEIVE)) {
				e = new Event(EventType.E72_RECEIVE_SIMPLEX);
			}
			break;
		case CONNECTING_T:
			if (state.getDuplex().equals(Duplex.FULL_DUPLEX)) {
				e = new Event(EventType.E2_CONNECTING_T);
			} else if (state.getDuplex().equals(Duplex.HALF_DUPLEX)) {
				e = new Event(EventType.E31_CONNECTING_T);
			} else if (state.getDuplex().equals(Duplex.SIMPLEX_TRANSMIT)) {
				e = new Event(EventType.E71_TRANSMIT_SIMPLEX);
			}
			break;
		case LOCAL_COMM_CHANGE:
			if (state.getDuplex().equals(Duplex.FULL_DUPLEX)) {
				e = new Event(EventType.E12_LOCAL_COMM_CHANGE, e.getArg());
			} else if (state.getDuplex().equals(Duplex.HALF_DUPLEX)) {
				e = new Event(EventType.E62_LOCAL_COMM_CHANGE, e.getArg());
			}
			break;
		case LNMD:
			if (state.getDuplex().equals(Duplex.FULL_DUPLEX)) {
				e = new Event(EventType.E21_LNMD);
			} else if (state.getDuplex().equals(Duplex.HALF_DUPLEX)) {
				e = new Event(EventType.E51_LNMD);
			}
			break;
		}
		if (state.getDuplex().equals(Duplex.FULL_DUPLEX)) {
			fullDuplexMac.handle(e);
		} else if (state.getDuplex().equals(Duplex.HALF_DUPLEX)) {
			halfDuplexMac.handle(e);
		} else if (state.getDuplex().equals(Duplex.SIMPLEX_RECEIVE) || state.getDuplex().equals(Duplex.SIMPLEX_TRANSMIT)) {
			simplexMac.handle(e);
		}
		frameSublayer.handle(e);
	}

	@Override
	public void run() {
		while (!Thread.currentThread().isInterrupted()) {
			try {
				Event event = state.getQueue().take();
				if (LOG.isDebugEnabled()) {
					LOG.debug("got event: {} queue size: {}", event.getType(), state.getQueue().size());
				}
				if (event.getType().equals(EventType.STOP)) {
					break;
				}
				MacState before = state.getState();
				handle(event);
				MacState after = state.getState();
				if (!before.equals(after)) {
					STATE_LOG.info(String.format("%20s: %-20s -> %-20s", event.getType(), before, state.getState()));
				} else {
					STATE_LOG.debug(String.format("%20s: %-20s -> %-20s", event.getType(), before, state.getState()));
				}
			} catch (InterruptedException e) {
				break;
			} catch (Exception e) {
				LOG.error("unable to handle", e);
				continue;
			}
		}
	}

	public void startSession() {
		sendEvent(new Event(EventType.CONNECTING_T));
	}

	public void waitForSession() {
		sendEvent(new Event(EventType.CONNECTING_L));
	}

	public void stopSession() {
		sendEvent(new Event(EventType.LNMD));
	}

	// going into inactive straightaway
	public void reset() {
		sendEvent(new Event(EventType.E28_INACTIVE));
	}

	public int setCommParameters(Object directive) {
		if (directive == null) {
			return ErrorCode.INVALID_ARGUMENT_LENGTH.ordinal();
		}
		int code = frameSublayer.validateCommParameters(directive);
		if (code != 0) {
			return code;
		}
		sendEvent(new Event(EventType.LOCAL_COMM_CHANGE, directive));
		return ErrorCode.SUCCESS.ordinal();
	}

	public int clearQueue(QueueType type) {
		if (type == null) {
			return ErrorCode.INVALID_ARGUMENT_LENGTH.ordinal();
		}
		sendEvent(new Event(EventType.CLEAR_QUEUE, type));
		return 0;
	}

	public int setDuplex(Duplex duplex) {
		if (duplex == null) {
			return ErrorCode.INVALID_ARGUMENT_LENGTH.ordinal();
		}
		sendEvent(new Event(EventType.DUPLEX, duplex));
		return ErrorCode.SUCCESS.ordinal();
	}

	public int startTimeTagging(int numberOfSamples) {
		// both type1 and type4/5 have the same length for time sample field
		int code = Util.validateInt(6, "time_sample", numberOfSamples);
		if (code != 0) return code;
		sendEvent(new Event(EventType.TIME_TAGGING, numberOfSamples));
		return ErrorCode.SUCCESS.ordinal();
	}

	public int distributeTime(TimeDistribution directive) {
		// transceiverClock is the full 8byte long
		int code = Util.validateInt(3 * 8, "sendSideDelay", directive.getSendSideDelay());
		if (code != 0) return code;
		code = Util.validateInt(3 * 8, "oneWayLightTime", directive.getOneWayLightTime());
		if (code != 0) return code;
		directive.setType(TimeDistributionType.TIME_TRANSFER);
		sendEvent(new Event(EventType.TIME_DISTRIBUTION, directive));
		return ErrorCode.SUCCESS.ordinal();
	}

	public void calculateTimeDelay(long initialTimeMillis) {
		sendEvent(new Event(EventType.TIME_DELAY_CALCULATION, initialTimeMillis));
	}

	@SuppressWarnings("unchecked")
	public void send(byte[] packet, QualityOfService qos, int portId) {
		List<Object> frames = frameSublayer.createUFrame(packet, qos, portId);
		// can be empty when current v2 frame buffer was not fully filled
		if (frames.isEmpty()) {
			return;
		}
		// will lock if no free space available in queues
		if (qos.equals(QualityOfService.EXPEDITED)) {
			for (Object cur : frames) {
				try {
					state.getExpQueue().put(cur);
				} catch (InterruptedException e) {
					Thread.currentThread().interrupt();
					return;
				}
			}
		} else {
			for (Object cur : frames) {
				try {
					state.getSeqQueue().put(cur);
				} catch (InterruptedException e) {
					Thread.currentThread().interrupt();
					return;
				}
			}
		}
		// event queue might be polluted by these events
		// when persistent activity is ongoing and queues not processed
		// shouldn't be a big issue to discard them and start processing queues
		// naturally
		sendEvent(new Event(EventType.UFRAME));
	}

	@Override
	public void onRx(byte[] data) {
		Object frame = frameSublayer.onRx(data);
		if (frame == null) {
			return;
		}

		PduTypeId type = TransferFrameUtil.getPduTypeId(frame);
		SourceDestination sourceDestination = TransferFrameUtil.getSourceDestination(frame);
		int scid = TransferFrameUtil.getScid(frame);
		byte[] payload = TransferFrameUtil.getPayload(frame);
		if (type == null || sourceDestination == null || scid < 0 || payload == null) {
			return;
		}

		boolean invalidDestination = sourceDestination.equals(SourceDestination.DESTINATION) && localScid != scid;
		if (invalidDestination) {
			LOG.debug("discard frame to {}", scid);
			return;
		}

		boolean invalidSource = sourceDestination.equals(SourceDestination.SOURCE) && testSource && remoteScid != -1 && remoteScid != scid;
		if (invalidSource) {
			callback.handle(new ControllerEvent(ControllerEventType.INVALID_FRAME_SOURCE, scid));
			return;
		}

		if (sourceDestination.equals(SourceDestination.SOURCE) && testSource && remoteScid == -1) {
			remoteScid = scid;
			LOG.info("test source validation started for {}", remoteScid);
		}

		// TODO discard idle packets?

		// once time tagging is off and asm sampling is off, then rx asm time will be 0
		// and skipped
		if (rxAsmTime != 0) {
			sendEvent(new Event(EventType.ASM_RX, new TimeTag(rxAsmTime, TransferFrameUtil.getSequenceNumber(frame), TransferFrameUtil.getQualityOfService(frame), TimeTagType.RX)));
			rxAsmTime = 0;
		}

		if (type.equals(PduTypeId.SPDU)) {
			sendEvent(new Event(EventType.SPDU, frameSublayer.decodeSpdu(payload)));
		} else {
			QualityOfService qos = TransferFrameUtil.getQualityOfService(frame);
			boolean acceptFrame = true;
			if (qos.equals(QualityOfService.EXPEDITED)) {
				sendEvent(new Event(EventType.RE3_EXPEDITED_FRAME));
			} else {
				int ns = TransferFrameUtil.getSequenceNumber(frame);
				if (vrFromCallback == ns) {
					vrFromCallback = (vrFromCallback + 1) & maxSequenceNumber;
					sendEvent(new Event(EventType.RE4_SEQUENCE_FRAME, vrFromCallback));
				} else if (frameSublayer.modCompare(ns, vrFromCallback) > 0) {
					sendEvent(new Event(EventType.RE5_GAP_DETECTED));
					acceptFrame = false;
					LOG.info("gap detected. expected: {} got: {}", vrFromCallback, ns);
				} else if (frameSublayer.modCompare(ns, vrFromCallback) < 0) {
					// progressive retransmission will re-send frames until corresponding PLCW
					// received
					acceptFrame = false;
				}
			}
			if (acceptFrame) {
				frameSublayer.onFrame(rxCallback, frame);
			}
		}
	}

	@Override
	public void onTx() {
		sendEvent(new Event(EventType.TX_COMPLETE));
	}

	@Override
	public void onCarrierAcquired() {
		sendEvent(new Event(EventType.CARRIER_ACQUIRED));
	}

	@Override
	public void onCarrierLost() {
		sendEvent(new Event(EventType.CARRIER_LOST));
	}

	@Override
	public void onSymbolLock() {
		sendEvent(new Event(EventType.SYMBOL_LOCK));
	}

	@Override
	public void onSymbolUnlock() {
		sendEvent(new Event(EventType.SYMBOL_UNLOCK));
	}

	@Override
	public void onAsmRx() {
		rxAsmTime = System.currentTimeMillis();
	}

	@Override
	public void onAsmTx() {
		sendEvent(new Event(EventType.ASM_TX, System.currentTimeMillis()));
	}

	public void start() {
		frameSublayer.start();
		timer.start();
		t.start();
	}

	public void stop() {
		sendEvent(new Event(EventType.STOP));
		t.interrupt();
		try {
			t.join(config.getDuration("shutdown_duration"));
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
		}
		timer.stop();
		frameSublayer.stop();
	}

	private void sendEvent(Event e) {
		try {
			state.getQueue().put(e);
		} catch (InterruptedException e1) {
			Thread.currentThread().interrupt();
		}
	}

	private void startTimeTaggingInternal(int numberOfFramesToTag) {
		// time-tag equal number of rx and tx frames
		state.setRxFramesToTimeTag(numberOfFramesToTag);
		state.setTxFramesToTimeTag(numberOfFramesToTag);
		state.setTags(new ArrayList<>());
		transceiver.enableAsmRxCallback(true);
		transceiver.enableAsmTxCallback(true);
	}

	private boolean isTimeTaggingActive() {
		return state.getRxFramesToTimeTag() != 0 || state.getTxFramesToTimeTag() != 0;
	}

	// used on tests only
	State getState() {
		return state;
	}

	void setVrFromCallback(int vrFromCallback) {
		this.vrFromCallback = vrFromCallback;
	}

	private boolean isDataServices() {
		// full duplex
		if (state.getState().equals(MacState.S40_DATA_SERVICES)) {
			return true;
		}
		// half duplex
		if (state.getState().equals(MacState.S60_DATA_SERVICES_RECEIVE) || state.getState().equals(MacState.S50_DATA_SERVICES_SEND) || state.getState().equals(MacState.S61_AWAITING_FIRST_FRAME)) {
			return true;
		}
		// simplex
		if (state.getState().equals(MacState.S71_SIMPLEX_TRANSMIT) || state.getState().equals(MacState.S72_SIMPLEX_RECEIVE)) {
			return true;
		}
		return false;
	}

}
