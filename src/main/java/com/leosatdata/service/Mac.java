package com.leosatdata.service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.LinkedBlockingQueue;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.leosatdata.ccsds2110b6.model.DfcId;
import com.leosatdata.ccsds2110b6.model.PduTypeId;
import com.leosatdata.ccsds2110b6.model.QualityOfService;
import com.leosatdata.ccsds2110b6.model.SourceDestination;
import com.leosatdata.model.ControlParameters;
import com.leosatdata.model.ControllerEvent;
import com.leosatdata.model.ControllerEventType;
import com.leosatdata.model.Event;
import com.leosatdata.model.EventType;
import com.leosatdata.model.Link2Establishment;
import com.leosatdata.model.LinkEstablishment;
import com.leosatdata.model.MacDuplex;
import com.leosatdata.model.MacMode;
import com.leosatdata.model.PersistentActivity;
import com.leosatdata.model.PlExtensions;
import com.leosatdata.model.Plcw;
import com.leosatdata.model.Spdu;
import com.leosatdata.model.StatusReport;
import com.leosatdata.model.TimeDistribution;
import com.leosatdata.model.TransceiverEvent;
import com.leosatdata.model.TransceiverEventType;
import com.leosatdata.model.TxRxParameters;
import com.leosatdata.model.Type1Directive;
import com.leosatdata.model.Type4Directive;
import com.leosatdata.model.Type5Directive;
import com.leosatdata.util.BitInputStream;
import com.leosatdata.util.BitOutputStream;
import com.leosatdata.util.Configuration;
import com.leosatdata.util.SpduParser;
import com.leosatdata.util.SpduParserCallback;
import com.leosatdata.util.TransceiverUtil;

public class Mac implements Runnable, SymbolInLockCallback, CarrierCallback, SpduCallback, SpduParserCallback {

	private final static Logger LOG = LoggerFactory.getLogger(Mac.class);
	private final static int HAIL_RESPONSE_FRAME = 0;
	private final static int HAIL_RESPONSE_SYMBOL_IN_LOCK = 1;

	private final Fop fop;
	private final Farm farm;
	private final Configuration config;
	private final NodeCallback callback;
	private final SpduParser spduParser;

	private final LinkedBlockingQueue<Event> queue = new LinkedBlockingQueue<>();
	private Timer waitTimer;
	private TimerTask waitTimerTask;

	private Spdu currentType;

	private MacMode mode = MacMode.INACTIVE;
	private boolean transmit = false;
	private boolean hailCarrierReceived = false;
	private boolean symbolInLock = false;
	private MacDuplex duplex = MacDuplex.FULL; // FIXME check defaults
	private FrameSublayer frameSublayer;
	private int ss;
	private int x;
	private int y;
	private int z;
	private final Thread t;
	private int currentHailAttempt = 0;

	private Object commValueBuffer;

	public Mac(Configuration config, Transceiver transceiver, FrameSublayer frameSublayer, NodeCallback callback) {
		this.config = config;
		this.callback = callback;
		this.fop = new Fop(transceiver);
		this.farm = new Farm();
		this.frameSublayer = frameSublayer;
		this.frameSublayer.setSpduCallback(this);
		this.spduParser = new SpduParser(this);
		t = new Thread(this, "mac-thread");
	}

	public void start() {
		t.start();
		waitTimer = new Timer("wt"); // this will start Thread
//		fop.start();
//		// FIXME start fop / farm
	}

	@Override
	public void run() {
		while (!Thread.currentThread().isInterrupted()) {
			try {
				handle(queue.take());
			} catch (InterruptedException e) {
				break;
			}
		}
	}

	public void stop() {
		t.interrupt();
		try {
			// FIXME termination timeout
			t.join();
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
		}
		if (waitTimer != null) {
			waitTimer.cancel();
		}
	}

	private void handle(Event e) {
		switch (e.getType()) {
		case E2_CONNECTING_T:
			LOG.info("attempting hail: {} of {}", currentHailAttempt, config.getInteger("hail_lifetime", 0));
			commValueBuffer = null;
			if (currentType != null) {
				commValueBuffer = encode(currentType);
			} else {
				LOG.error("local comm is not configured");
			}
			if (commValueBuffer == null) {
				callback.handle(createNotification("hail", false));
				return;
			}

			mode = MacMode.CONNECTING_T;
			frameSublayer.sendUserFrame(new TransceiverEvent(TransceiverEventType.START_CARRIER, TransceiverUtil.createHailTxRx(config)));
			scheduleWait(EventType.E4_CARRIER_ONLY_TIMEOUT, config.getDuration("carrier_only_duration"));
			break;
		case E4_CARRIER_ONLY_TIMEOUT:
			frameSublayer.sendControlFrame(new TransceiverEvent(TransceiverEventType.START_IDLE));
			scheduleWait(EventType.E5_ACQUISITION_IDLE_TIMEOUT, config.getDuration("acquisition_idle_duration"));
			break;
		case E5_ACQUISITION_IDLE_TIMEOUT:
			frameSublayer.sendControlFrame(commValueBuffer);
			break;
		case E6_OUTPUT_FIFO_EMPTY:
			// FIXME fifo empty
			scheduleWait(EventType.E7_TAIL_IDLE_TIMEOUT, config.getDuration("tail_idle_duration"));
			break;
		case E7_TAIL_IDLE_TIMEOUT:
			hailCarrierReceived = false;
			frameSublayer.sendControlFrame(new TransceiverEvent(TransceiverEventType.START_RX));
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
		case E9_VALID_FRAME_RECEIVED:
			frameSublayer.sendControlFrame(new TransceiverEvent(TransceiverEventType.SWITCH_TO_USER));
			waitTimerTask.cancel();
			callback.handle(createNotification("hail", true));
			currentHailAttempt = 0;
			// switch over to the agreed
			frameSublayer.sendUserFrame(new TransceiverEvent(TransceiverEventType.START_CARRIER, currentType));
			scheduleWait(EventType.E10_CARRIER_ONLY_TIMEOUT, config.getDuration("carrier_only_duration"));
			break;
		case E10_CARRIER_ONLY_TIMEOUT:
			frameSublayer.sendControlFrame(new TransceiverEvent(TransceiverEventType.START_IDLE));
			scheduleWait(EventType.E11_ACQUISITION_IDLE_TIMEOUT, config.getDuration("acquisition_idle_duration"));
			break;
		case E11_ACQUISITION_IDLE_TIMEOUT:
			// FIXME enable U-frames coming
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
			// switch to user queue. otherwise sendUserFrame won't work
			// FIXME somehow block user's frame until hail completes
			frameSublayer.sendControlFrame(new TransceiverEvent(TransceiverEventType.SWITCH_TO_USER));
			sendEvent(new Event(EventType.E2_CONNECTING_T));
			break;

		case E12_LOCAL_COMM_CHANGE:
			if (e.getArg() == null) {
				LOG.error("received comm change without payload");
				return;
			}
			if (e.getArg() instanceof Type1Directive) {
				Type1Directive obj = (Type1Directive) e.getArg();
				if (currentType == null) {
					currentType = obj;
				} else {
					((Type1Directive) currentType).merge(obj);
				}
			} else if (e.getArg() instanceof Type4Directive) {
				Type4Directive obj = (Type4Directive) e.getArg();
				if (currentType == null) {
					currentType = obj;
				} else {
					((Type4Directive) currentType).merge(obj);
				}
			} else if (e.getArg() instanceof Type5Directive) {
				Type5Directive obj = (Type5Directive) e.getArg();
				if (currentType == null) {
					currentType = obj;
				} else {
					((Type5Directive) currentType).merge(obj);
				}
			} else {
				LOG.error("unknown arg type: {}", e.getArg());
				return;
			}
			if (mode.equals(MacMode.ACTIVE)) {
				// FIXME send remote change
			}
			break;
		default:
			LOG.error("unknown event type: {}", e.getType());
		}
	}

	// SET MODE directive
	public void setMode(MacMode mode) {
		switch (mode) {
		case INACTIVE:
//			transceiver.setModulation(false);
//			transceiver.stopRx();
//			transceiver.stopTx();
//			transmit = false;
//			persistence = false;
//			ss = 0;
//			x = 0;
//			y = 0;
//			z = 0;
			// cancel all timers
			// reset sequence counters
			break;
		case CONNECTING_T:
			sendEvent(new Event(EventType.E2_CONNECTING_T));
			break;
		case CONNECTING_L:
//			transceiver.startRx();
//			transceiver.stopTx();
			break;
		// FIXME active?
		default:
			break;
		}
	}

	// SET INITIALIZE MODE directive
	public void setInitializeMode() {
		setMode(MacMode.INACTIVE);
		fop.setInitializeMode();
		farm.setInitializeMode();
	}

	public void setLocalCommChange(TxRxParameters tx, TxRxParameters rx, PlExtensions txExt, PlExtensions rxExt) {
		if (!config.getProperty("frame_version").equals("3")) {
			throw new IllegalArgumentException("expected proximity-1");
		}
		Type1Directive obj = new Type1Directive();
		obj.setRxPlExtensions(rxExt);
		obj.setTxPlExtensions(txExt);
		obj.setRxParams(rx);
		obj.setTxParams(tx);
		sendEvent(new Event(EventType.E12_LOCAL_COMM_CHANGE, obj));
	}

	public void setLocalCommChange(LinkEstablishment rxLink, LinkEstablishment txLink) {
		// FIXME when to send type4 vs type5?
		if (!config.getProperty("frame_version").equals("4")) {
			throw new IllegalArgumentException("expected USLP");
		}
		Type4Directive obj = new Type4Directive();
		obj.setRxLink(rxLink);
		obj.setTxLink(txLink);
		sendEvent(new Event(EventType.E12_LOCAL_COMM_CHANGE, obj));
	}

	public void setLocalCommChange(Link2Establishment rxLink, Link2Establishment txLink) {
		if (!config.getProperty("frame_version").equals("4")) {
			throw new IllegalArgumentException("expected USLP");
		}
		Type5Directive obj = new Type5Directive();
		obj.setRxLink(rxLink);
		obj.setTxLink(txLink);
		sendEvent(new Event(EventType.E12_LOCAL_COMM_CHANGE, obj));
	}

	public void setCommunicationsValueBuffer() {

	}

	// This directive initiates the session termination process.
	public void setNoMoreData() {

	}

	public void setDuplex(MacDuplex duplex) {
		this.duplex = duplex;
	}

	public void setReceivingScidBuffer() {

	}

	public void readStatus() {
		// FIXME Read Status shall selectively read the local status registers and
		// buffers (including timing services) within the transceiver.
	}

	// remote
	void setTxParameters(TxRxParameters tx) {

	}

	void setRxParameters(TxRxParameters rx) {

	}

	void setRxPlExtensions(PlExtensions ext) {

	}

	void setTxPlExtensions(PlExtensions ext) {

	}

	void setControlParameters(ControlParameters params) {

	}

	private void sendEvent(Event e) {
		try {
			queue.put(e);
		} catch (InterruptedException e1) {
			Thread.currentThread().interrupt();
			LOG.error("can't send event", e1);
		}
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

	private Object encode(Spdu spdu) {
		ByteArrayOutputStream baos = new ByteArrayOutputStream();
		try (BitOutputStream bos = new BitOutputStream(baos)) {
			SpduParser.encode(spdu, bos);
		} catch (IOException e1) {
			LOG.error("can't serialize spdu", e1);
			return null;
		}
		byte[] payload = baos.toByteArray();
		String frameVersion = config.getProperty("frame_version");
		if (frameVersion.equals("3")) {
			com.leosatdata.ccsds2110b6.model.TransferFrameHeader header = new com.leosatdata.ccsds2110b6.model.TransferFrameHeader();
			header.setVersionNumber(com.leosatdata.ccsds2110b6.util.TransferFrameParser.VERSION_NUMBER);
			header.setQualityOfService(QualityOfService.EXPEDITED);
			header.setPduTypeId(PduTypeId.SPDU);
			header.setDfcId(DfcId.PACKETS);
			header.setSourceOrDestination(SourceDestination.SOURCE);
			header.setScid(config.getInteger("local_spacecraft_id", 0));
			header.setPortId(0); // not used in P frames
			header.setPcid(config.getInteger("local_pcid", 0));
			header.setFrameLength(payload.length);
			com.leosatdata.ccsds2110b6.model.TransferFrame frame = new com.leosatdata.ccsds2110b6.model.TransferFrame();
			frame.setHeader(header);
			frame.setDataField(payload);
			return frame;
		} else if (frameVersion.equals("4")) {
			com.leosatdata.ccsds7321b3.model.TransferFramePrimaryHeader header = new com.leosatdata.ccsds7321b3.model.TransferFramePrimaryHeader();
			header.setVersionNumber(com.leosatdata.ccsds7321b3.util.TransferFrameParser.VERSION_NUMBER);
			header.setScid(config.getInteger("local_spacecraft_id", 0));
			header.setSourceOrDestination(SourceDestination.SOURCE);
			header.setVcid(0); // not clear
			header.setMapid(0); // not clear
			header.setHeaderTruncated(false);
			header.setFrameLength(payload.length);
			header.setSequenceControl(QualityOfService.EXPEDITED);
			header.setProtocolControl(PduTypeId.SPDU);
			header.setOcf(false);
			com.leosatdata.ccsds7321b3.model.TransferFrame frame = new com.leosatdata.ccsds7321b3.model.TransferFrame();
			frame.setHeader(header);
			frame.setDataField(payload);
			return frame;
		} else {
			LOG.error("unsupported frame version: {}", frameVersion);
			return null;
		}
	}

	@Override
	public void onCarrierAcquired() {
		sendEvent(new Event(EventType.E8_1_CARRIER_RECEIVED));
	}

	@Override
	public void onSymbolInLock() {
		sendEvent(new Event(EventType.E8_2_SYMBOL_IN_LOCK));
	}

	@Override
	public void onSpdu(byte[] spdu) {
		try {
			spduParser.decode(new BitInputStream(spdu));
		} catch (IOException e) {
			LOG.error("unable to parse spdu", e);
		}
	}

	@Override
	public void onSpduTx() {
		// FIXME not only hail-based directives here / can be plcw tx callback
		sendEvent(new Event(EventType.E6_OUTPUT_FIFO_EMPTY));
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

	@Override
	public void onPlsw(Plcw message) {
		// FIXME not sure if Plcw is expected or status report
		sendEvent(new Event(EventType.E9_VALID_FRAME_RECEIVED));
	}

	@Override
	public void onTimeDistribution(TimeDistribution timeDistribution) {
		// TODO Auto-generated method stub

	}

	@Override
	public void onType1Directive(Type1Directive decode) {
		// TODO Auto-generated method stub

	}

	@Override
	public void onType4Directive(Type4Directive decode) {
		// TODO Auto-generated method stub

	}

	@Override
	public void onType5Directive(Type5Directive decode) {
		// TODO Auto-generated method stub

	}

	@Override
	public void onStatusReport(StatusReport decode) {
		// TODO Auto-generated method stub

	}

}
