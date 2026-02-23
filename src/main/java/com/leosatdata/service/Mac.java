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
import com.leosatdata.model.PersistentActivityType;
import com.leosatdata.model.PlExtensions;
import com.leosatdata.model.Spdu;
import com.leosatdata.model.TxRxParameters;
import com.leosatdata.model.Type1Directive;
import com.leosatdata.model.Type4Directive;
import com.leosatdata.model.Type5Directive;
import com.leosatdata.util.BitOutputStream;
import com.leosatdata.util.Configuration;
import com.leosatdata.util.SpduParser;
import com.leosatdata.util.TransceiverUtil;

public class Mac implements Runnable, TxCallback, RxCallback {

	private final static Logger LOG = LoggerFactory.getLogger(Mac.class);
	private final static int HAIL_RESPONSE_FRAME = 0;
	private final static int HAIL_RESPONSE_SYMBOL_IN_LOCK = 1;

	private final Fop fop;
	private final Farm farm;
	private final Transceiver transceiver;
	private final Configuration config;
	private final NodeCallback callback;

	private final LinkedBlockingQueue<Event> queue = new LinkedBlockingQueue<>();
	private Timer waitTimer;

	private Type1Directive currentType1;
	private Type4Directive currentType4;
	private Type5Directive currentType5;

	private MacMode mode = MacMode.INACTIVE;
	private boolean transmit = false;
	private boolean persistence = false;
	private boolean hailCarrierReceived = false;
	private boolean symbolInLock = false;
	private MacDuplex duplex = MacDuplex.FULL; // FIXME check defaults
	private int ss;
	private int x;
	private int y;
	private int z;
	private final Thread t;
	private int expFsn = 0;
	private int currentHailAttempt = 0;

	private byte[] commValueBuffer;

	public Mac(Configuration config, Transceiver transceiver, NodeCallback callback) {
		this.config = config;
		this.callback = callback;
		this.transceiver = transceiver;
		this.transceiver.setTxCallback(this);
		this.fop = new Fop(transceiver);
		this.farm = new Farm();
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
			if (currentType1 != null) {
				commValueBuffer = encode(currentType1);
			} else if (currentType4 != null) {
				commValueBuffer = encode(currentType4);
			} else if (currentType5 != null) {
				commValueBuffer = encode(currentType5);
			} else {
				LOG.error("local comm is not configured");
			}
			if (commValueBuffer == null) {
				callback.handle(createHailNotification(false));
				return;
			}

			persistence = true;
			mode = MacMode.CONNECTING_T;
			configureTransceiverForHail();
			transceiver.startCarrier();
			scheduleWait(EventType.E4_CARRIER_ONLY_TIMEOUT, config.getDuration("carrier_only_duration"));
			break;
		case E4_CARRIER_ONLY_TIMEOUT:
			transceiver.startIdle();
			scheduleWait(EventType.E5_ACQUISITION_IDLE_TIMEOUT, config.getDuration("acquisition_idle_duration"));
			break;
		case E5_ACQUISITION_IDLE_TIMEOUT:
			transceiver.send(commValueBuffer);
			break;
		case E6_OUTPUT_FIFO_EMPTY:
			scheduleWait(EventType.E7_TAIL_IDLE_TIMEOUT, config.getDuration("tail_idle_duration"));
			break;
		case E7_TAIL_IDLE_TIMEOUT:
			transceiver.stopIdle();
			hailCarrierReceived = false;
			transceiver.startRx();
			scheduleWait(EventType.E8_HAIL_WAIT_TIMEOUT, config.getDuration("hail_wait_duration"));
			break;
		case E8_1_CARRIER_RECEIVED:
			hailCarrierReceived = true;
			break;
		case E8_2_SYMBOL_IN_LOCK:
			symbolInLock = true;
			if (config.getInteger("hail_response", 0) == HAIL_RESPONSE_SYMBOL_IN_LOCK) {
				callback.handle(createHailNotification(true));
			}
			break;
		case E8_HAIL_WAIT_TIMEOUT:
			if (hailCarrierReceived) {
				callback.handle(new ControllerEvent(ControllerEventType.CARRIER_ONLY_RECEIVED));
			}
			currentHailAttempt++;
			int maxAttempts = config.getInteger("hail_lifetime", 0);
			if (maxAttempts != 0 && currentHailAttempt >= maxAttempts) {
				currentHailAttempt = 0;
				callback.handle(createHailNotification(false));
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
				if (currentType1 == null) {
					currentType1 = obj;
				} else {
					currentType1.merge(obj);
				}
			} else if (e.getArg() instanceof Type4Directive) {
				Type4Directive obj = (Type4Directive) e.getArg();
				if (currentType4 == null) {
					currentType4 = obj;
				} else {
					currentType4.merge(obj);
				}
			} else if (e.getArg() instanceof Type5Directive) {
				Type5Directive obj = (Type5Directive) e.getArg();
				if (currentType5 == null) {
					currentType5 = obj;
				} else {
					currentType5.merge(obj);
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

//
//	public void send(TransferFrame frame) {
//		if (frame == null || frame.getHeader() == null) {
//			return;
//		}
//		// accept only user data from users
//		if (frame.getHeader().getPduTypeId().equals(PduTypeId.SPDU)) {
//			// FIXME some error reporting?
//			return;
//		}
//		fop.send(frame);
//	}
//

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
		waitTimer.schedule(new TimerTask() {

			@Override
			public void run() {
				sendEvent(new Event(type));
			}
		}, duration);
	}

	private void configureTransceiverForHail() {
		// frame verion is the closest parameter to control hailing parameters
		String frameVersion = config.getProperty("frame_version");
		if (frameVersion.equals("3")) {
			TransceiverUtil.applyParameters(TransceiverUtil.createDefaultProximityHail(config), config, transceiver);
		} else if (frameVersion.equals("4")) {
			TransceiverUtil.applyParameters(TransceiverUtil.createDefaultUslp(config), transceiver);
		} else {
			throw new IllegalArgumentException("unsupported frame_version: " + frameVersion);
		}
	}

	private byte[] encode(Spdu spdu) {
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
			header.setVersionNumber(0b10); // version 3
			header.setQualityOfService(QualityOfService.EXPEDITED);
			header.setPduTypeId(PduTypeId.SPDU);
			header.setDfcId(DfcId.PACKETS);
			header.setSourceOrDestination(SourceDestination.SOURCE);
			header.setScid(config.getInteger("local_spacecraft_id", 0));
			header.setPortId(0); // not used in P frames
			header.setPcid(config.getInteger("local_pcid", 0));
			header.setFrameLength(payload.length);
			header.setFrameSequenceNumber(expFsn++);
			com.leosatdata.ccsds2110b6.model.TransferFrame frame = new com.leosatdata.ccsds2110b6.model.TransferFrame();
			frame.setHeader(header);
			frame.setDataField(payload);
			baos = new ByteArrayOutputStream();
			try (BitOutputStream bos = new BitOutputStream(baos)) {
				com.leosatdata.ccsds2110b6.util.TransferFrameParser.encode(frame, bos);
			} catch (IOException e1) {
				LOG.error("can't serialize transferframe3", e1);
				return null;
			}
			return baos.toByteArray();
		} else if (frameVersion.equals("4")) {
			com.leosatdata.ccsds7321b3.model.TransferFramePrimaryHeader header = new com.leosatdata.ccsds7321b3.model.TransferFramePrimaryHeader();
			header.setVersionNumber(0b1100); // version 4
			header.setScid(config.getInteger("local_spacecraft_id", 0));
			header.setSourceOrDestination(SourceDestination.SOURCE);
			header.setVcid(0); // not clear
			header.setMapid(0); // not clear
			header.setHeaderTruncated(false);
			header.setFrameLength(payload.length);
			header.setSequenceControl(QualityOfService.EXPEDITED);
			header.setProtocolControl(PduTypeId.SPDU);
			header.setOcf(false);
			header.setVcfCountLength(0b100); // 4 bytes for vcf count
			header.setVcfCount(expFsn++);
			com.leosatdata.ccsds7321b3.model.TransferFrame frame = new com.leosatdata.ccsds7321b3.model.TransferFrame();
			frame.setHeader(header);
			frame.setDataField(payload);
			baos = new ByteArrayOutputStream();
			try (BitOutputStream bos = new BitOutputStream(baos)) {
				com.leosatdata.ccsds7321b3.util.TransferFrameParser.encode(frame, bos);
			} catch (IOException e1) {
				LOG.error("can't serialize transferframe4", e1);
				return null;
			}
			return baos.toByteArray();
		} else {
			LOG.error("unsupported frame version: {}", frameVersion);
			return null;
		}
	}

	@Override
	public void onTx() {
		sendEvent(new Event(EventType.E6_OUTPUT_FIFO_EMPTY));
	}

	@Override
	public void onRx(byte[] data) {
		// TODO Auto-generated method stub

	}

	@Override
	public void onCarrierAcquired() {
		sendEvent(new Event(EventType.E8_1_CARRIER_RECEIVED));
	}

	@Override
	public void onSymbolInLock() {
		sendEvent(new Event(EventType.E8_2_SYMBOL_IN_LOCK));
	}

	private ControllerEvent createHailNotification(boolean success) {
		PersistentActivity activity = new PersistentActivity();
		activity.setType(PersistentActivityType.HAILING);
		activity.setSuccess(success);
		// not clear why OBC need these parameters
		activity.setLifetime(config.getInteger("hail_lifetime", 0));
		activity.setWaitingPeriod(config.getInteger("hail_wait_duration", 0));
		activity.setNotification(config.getInteger("hail_notification", 0));
		activity.setResponse(config.getInteger("hail_response", 0));
		return new ControllerEvent(ControllerEventType.PERSISTENT_ACTIVITY, activity);
	}

}
