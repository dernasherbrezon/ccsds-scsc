package com.leosatdata.service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.leosatdata.ccsds2110b6.model.DfcId;
import com.leosatdata.ccsds2110b6.model.PduTypeId;
import com.leosatdata.ccsds2110b6.model.QualityOfService;
import com.leosatdata.ccsds2110b6.model.SourceDestination;
import com.leosatdata.model.ControllerEvent;
import com.leosatdata.model.ControllerEventType;
import com.leosatdata.model.Event;
import com.leosatdata.model.EventType;
import com.leosatdata.model.PersistentActivity;
import com.leosatdata.model.Plcw;
import com.leosatdata.model.QueueType;
import com.leosatdata.model.Spdu;
import com.leosatdata.model.State;
import com.leosatdata.model.Type1Directive;
import com.leosatdata.model.Type4Directive;
import com.leosatdata.model.Type5Directive;
import com.leosatdata.model.Vr;
import com.leosatdata.util.BitInputStream;
import com.leosatdata.util.BitOutputStream;
import com.leosatdata.util.Configuration;
import com.leosatdata.util.SpduParser;
import com.leosatdata.util.SpduUtil;
import com.leosatdata.util.TransferFrameUtil;

public class FrameSublayer {

	private final static Logger LOG = LoggerFactory.getLogger(FrameSublayer.class);
	private final static Logger RX = LoggerFactory.getLogger("rx");
	private final static Logger TX = LoggerFactory.getLogger("tx");
	private final static int MAX_SEQUENCE_NUMBER_BYTES = 1; // 2^8
	private final static int MAX_SEQUENCE_NUMBER = 256; // 2^8

	private final State state;
	private final Configuration config;
	private final Transceiver transceiver;
	private final NodeCallback callback;
	private final int uFrameVersion;
	private final int pFrameVersion;
	private final int spduVersion;
	private final int localScid;
	private final int localPcid;
	private final int transmissionWindow;

	private final com.leosatdata.ccsds7320b4.util.TransferFrameParser framev2Parser;
	private final com.leosatdata.ccsds2110b6.util.TransferFrameParser framev3Parser;
	private final com.leosatdata.ccsds7321b3.util.TransferFrameParser framev4Parser;
	private final SpduParser spduParser;

	private Timer synchTimer;
	private TimerTask synchTimerTask;
	private int currentResyncAttempt = 0;

	public FrameSublayer(State state, Configuration config, Transceiver transceiver, NodeCallback callback) {
		this.state = state;
		this.config = config;
		this.transceiver = transceiver;
		this.uFrameVersion = config.getInteger("u_frame_version", -1);
		this.pFrameVersion = config.getInteger("frame_version", -1);
		this.spduVersion = config.getInteger("spdu_version", 1);
		this.localScid = config.getInteger("local_spacecraft_id", 0);
		this.localPcid = config.getInteger("local_pcid", 0);
		this.transmissionWindow = config.getInteger("transmission_window", 10);
		this.framev2Parser = new com.leosatdata.ccsds7320b4.util.TransferFrameParser(config);
		this.framev3Parser = new com.leosatdata.ccsds2110b6.util.TransferFrameParser(config);
		this.framev4Parser = new com.leosatdata.ccsds7321b3.util.TransferFrameParser(config);
		this.spduParser = new SpduParser(config);
		this.callback = callback;
	}

	public void start() {
		synchTimer = new Timer("synch"); // this will start Thread
	}

	public void stop() {
		if (synchTimer != null) {
			synchTimer.cancel();
		}
	}

	public void handle(Event e) {
		handleFarmp(e);
		// always attempt to send frame after handling event
		handleFopp(e);
		Object frame = null;

		Spdu spdu = state.getMacQueue().poll();
		if (spdu != null) {
			ByteArrayOutputStream baos = new ByteArrayOutputStream();
			try (BitOutputStream bos = new BitOutputStream(baos)) {
				spduParser.encode(spdu, bos);
			} catch (IOException e1) {
				LOG.error("cannot encode spdu", e);
				return;
			}
			frame = createPFrame(baos.toByteArray());
		}

		if (frame == null && state.isPersistence()) {
			return;
		}

		if (frame == null && state.isuFrameLastSent() && state.isNeedPlcw()) {
			state.setNeedPlcw(false);
			state.setuFrameLastSent(false);
			frame = createPFrame(createPlcw());
		}

		if (frame == null) {
			frame = state.getExpQueue().poll();
			if (frame != null) {
				state.setuFrameLastSent(true);
			}
		}

		if (frame == null) {
			frame = selectSeqFrame();
			if (frame != null) {
				state.setuFrameLastSent(true);
			}
		}

		if (frame == null && !state.isuFrameLastSent() && state.isNeedPlcw()) {
			state.setNeedPlcw(false);
			state.setuFrameLastSent(false);
			frame = createPFrame(createPlcw());
		}

		if (frame == null) {
			return;
		}

		int nextSeqNumber;
		QualityOfService qos = TransferFrameUtil.getQualityOfService(frame);
		if (qos.equals(QualityOfService.EXPEDITED)) {
			nextSeqNumber = state.getVes();
		} else {
			nextSeqNumber = state.getVvs();
		}

		TransferFrameUtil.setSequenceNumber(frame, nextSeqNumber);

		if (nextSeqNumber > MAX_SEQUENCE_NUMBER) {
			nextSeqNumber = 0;
		} else {
			nextSeqNumber++;
		}

		if (qos.equals(QualityOfService.EXPEDITED)) {
			// TODO notify obc about VE(S)
			state.setVes(nextSeqNumber);
		} else {
			// TODO notify obc about VS(S)
			state.setVvs(nextSeqNumber);
		}

		byte[] payload = encode(frame);
		if (payload == null) {
			return;
		}

		debug(false, frame);

		transceiver.send(payload);

		if (qos.equals(QualityOfService.SEQUENCE_CONTROLLED)) {
			state.getSentQueue().add(frame);
		}
	}

	// FOP-P State Table: SE1
	private Object selectSeqFrame() {
		if (state.getVvs() < state.getVs()) {
			// FIXME select frame to re-send
			return null;
		}
		if (!state.getSeqQueue().isEmpty() && state.getVs() - state.getNnr() < transmissionWindow) {
			return state.getSeqQueue().poll();
		}
		if (state.getNnr() < state.getVs()) {
			state.setVvs(state.getNnr());
			// FIXME initiate progressive retransmission
		}
		return null;
	}

	@SuppressWarnings("incomplete-switch")
	private void handleFarmp(Event e) {
		switch (e.getType()) {
		case SPDU:
			Vr vr = SpduUtil.getVr((Spdu) e.getArg());
			if (vr != null) {
				LOG.info("received set v(r) directive: {}", vr.getFrameSequenceNumber());
				state.setRs(false);
				state.setVr(vr.getFrameSequenceNumber());
				state.setNeedPlcw(true);
			}
			break;
		case RE3_EXPEDITED_FRAME:
			int nextFrameCounter = state.getExpeditedFrameCounter() + 1;
			if (nextFrameCounter > 0b111) {
				nextFrameCounter = 0;
			}
			state.setExpeditedFrameCounter(nextFrameCounter);
			break;
		case RE4_SEQUENCE_FRAME:
			state.setVr((Integer) e.getArg());
			state.setRs(false);
			state.setNeedPlcw(true);
			break;
		case RE5_GAP_DETECTED:
			state.setRs(true);
			state.setNeedPlcw(true);
			break;
		}
	}

	@SuppressWarnings("incomplete-switch")
	private void handleFopp(Event e) {
		switch (e.getType()) {
		case CLEAR_QUEUE:
			QueueType type = (QueueType) e.getArg();
			switch (type) {
			case SENT_QUEUE:
				state.getSentQueue().clear();
				break;
			case SEQ_QUEUE:
				state.getSeqQueue().clear();
				break;
			default:
				LOG.info("unknown queue type to clear: {}", type);
			}
			return;
		case SPDU:
			if (!(e.getArg() instanceof Plcw)) {
				break;
			}
			Plcw plcw = (Plcw) e.getArg();
			if (!validate(plcw)) {
				if (state.isResync()) {
					// ignore
				} else {
					LOG.info("invalid plcw received. cop-p lost synchronization");
					state.setVvs(state.getNnr());
					scheduleWait(EventType.SE4_SYNCH_TIMEOUT, config.getDuration("synch_timeout"));
				}
			} else {
				if (state.isResync()) {
					if (!plcw.isRetransmit() && plcw.getReportValue() == state.getNnr()) {
						LOG.info("re-sync completed successfully");
						state.setResync(false);
						state.setPersistence(false);
						if (synchTimerTask != null) {
							synchTimerTask.cancel();
						}
						callback.handle(createNotification("resync", true));
					}
				} else {
					Object seqMesage = null;
					// remove acked messages from the sent queue
					while ((seqMesage = state.getSentQueue().peek()) != null) {
						int seqNumber = TransferFrameUtil.getSequenceNumber(seqMesage);
						if (seqNumber <= plcw.getReportValue()) {
							try {
								state.getSentQueue().take();
							} catch (InterruptedException e1) {
								Thread.currentThread().interrupt();
								break;
							}
						} else {
							break;
						}
					}
					if (plcw.isRetransmit() || plcw.getReportValue() > state.getVvs()) {
						state.setVvs(plcw.getReportValue());
					}
					state.setRrr(plcw.isRetransmit());
					state.setNnr(plcw.getReportValue());
					if (synchTimerTask != null) {
						synchTimerTask.cancel();
					}
				}
			}
			break;
		case TX_COMPLETE:
			if (!state.isResync()) {
				break;
			}
			scheduleWait(EventType.SE4_1_RESYNCH_TIMEOUT, config.getDuration("resync_waiting_period"));
			break;
		case SE4_SYNCH_TIMEOUT:
			callback.handle(new ControllerEvent(ControllerEventType.COP_P_LOSS_SYNCHRONIZATION));
			if (config.getBoolean("resync_local")) {
				state.setRrr(false);
				state.setResync(true);
				state.setPersistence(true);
				currentResyncAttempt = 0;
				resynch();
			}
			break;
		case SE4_1_RESYNCH_TIMEOUT:
			// ignore if timer fired but activity already completed
			if (!state.isResync()) {
				break;
			}
			currentResyncAttempt++;
			int maxAttempts = config.getInteger("resync_lifetime", 0);
			if (maxAttempts != 0 && currentResyncAttempt >= maxAttempts) {
				currentResyncAttempt = 0;
				callback.handle(createNotification("resync", false));
			} else {
				resynch();
			}
			break;
		}
		return;
	}

	private void resynch() {
		Spdu vr = createVr();
		if (vr == null) {
			return;
		}
		state.getMacQueue().add(vr);
		LOG.info("attempting resync: {} of {}", (currentResyncAttempt + 1), config.getInteger("resync_lifetime", 0));
	}

	// note 5 from 6.2.3.3 FOP-P State Table
	private boolean validate(Plcw plcw) {
		if (plcw.getReportValue() < state.getNnr()) {
			LOG.info("received plcw with nr too small: {}", plcw.getReportValue());
			return false;
		}
		if (plcw.getReportValue() > state.getVs()) {
			LOG.info("received plcw with nr too large: {}", plcw.getReportValue());
			return false;
		}
		if (plcw.isRetransmit() && plcw.getReportValue() == state.getNnr()) {
			return false;
		}
		if (!plcw.isRetransmit() && state.isRrr() && plcw.getReportValue() == state.getNnr()) {
			return false;
		}
		return true;
	}

	public void debug(boolean rx, Object obj) {
		Logger log;
		if (rx) {
			log = RX;
		} else {
			log = TX;
		}
		if (!log.isInfoEnabled() || obj == null) {
			return;
		}

		PduTypeId type = null;
		byte[] data = null;
		List<Object> logData = new ArrayList<>();
		if (obj instanceof com.leosatdata.ccsds7320b4.model.TransferFrame) {
			com.leosatdata.ccsds7320b4.model.TransferFrame frame = (com.leosatdata.ccsds7320b4.model.TransferFrame) obj;
			type = PduTypeId.USER_DATA;
			data = frame.getDataField();
			logData.add(frame.getScid());
			logData.add(frame.getVcid());
			logData.add(frame.getVcFrameCount());
			logData.add(PduTypeId.USER_DATA);
			logData.add(QualityOfService.EXPEDITED);
		}
		if (obj instanceof com.leosatdata.ccsds2110b6.model.TransferFrame) {
			com.leosatdata.ccsds2110b6.model.TransferFrame frame = (com.leosatdata.ccsds2110b6.model.TransferFrame) obj;
			type = frame.getPduTypeId();
			data = frame.getDataField();
			logData.add(frame.getScid());
			logData.add(frame.getPortId());
			logData.add(frame.getFrameSequenceNumber());
			logData.add(frame.getPduTypeId());
			logData.add(frame.getQualityOfService());
		}
		if (obj instanceof com.leosatdata.ccsds7321b3.model.TransferFrame) {
			com.leosatdata.ccsds7321b3.model.TransferFrame frame = (com.leosatdata.ccsds7321b3.model.TransferFrame) obj;
			type = frame.getProtocolControl();
			data = frame.getDataField();
			logData.add(frame.getScid());
			logData.add(frame.getMapid());
			logData.add(frame.getVcfCount());
			logData.add(frame.getProtocolControl());
			logData.add(frame.getSequenceControl());
		}
		if (data == null) {
			return;
		}
		logData.add(data.length);

		String logMessage = "{}:{} /{}/{}/{}/{} bytes";
		if (type != null && type.equals(PduTypeId.SPDU)) {
			logMessage += "/{}";
			logData.add(decodeSpdu(data));
		}
		log.info(logMessage, logData.toArray());
	}

	private byte[] createPlcw() {
		Plcw plcw = new Plcw();
		plcw.setReportValue(state.getVr());
		plcw.setExpeditedFrameCounter(state.getExpeditedFrameCounter());
		plcw.setPcid(localPcid);
		plcw.setRetransmit(state.isRs());
		ByteArrayOutputStream baos = new ByteArrayOutputStream();
		try (BitOutputStream bos = new BitOutputStream(baos)) {
			spduParser.encode(plcw, bos);
		} catch (IOException e1) {
			LOG.error("cannot encode spdu", e1);
			return null;
		}
		return baos.toByteArray();
	}

	private Spdu createVr() {
		Vr vr = new Vr();
		vr.setFrameSequenceNumber(state.getNnr());
		if (spduVersion == Type1Directive.SPDU_VERSION) {
			Type1Directive result = new Type1Directive();
			result.setVr(vr);
			return result;
		}
		if (spduVersion == Type4Directive.SPDU_VERSION) {
			Type4Directive result = new Type4Directive();
			result.setVr(vr);
			return result;
		}
		if (spduVersion == Type5Directive.SPDU_VERSION) {
			Type5Directive result = new Type5Directive();
			result.setVr(vr);
			return result;
		}
		LOG.error("unable to create vr. unknown spdu version: {}", spduVersion);
		return null;
	}

	@SuppressWarnings("rawtypes")
	public List createUFrame(byte[] payload, QualityOfService qos, int portId) {
		if (uFrameVersion == 2) {
			return framev2Parser.createUFrame(payload, qos, portId);
		}
		if (uFrameVersion == 3) {
			return framev3Parser.createUFrame(payload, qos, portId);
		}
		if (uFrameVersion == 4) {
			return framev4Parser.createUFrame(payload, qos, portId);
		}
		return Collections.emptyList();
	}

	private Object createPFrame(byte[] payload) {
		if (pFrameVersion == 3) {
			com.leosatdata.ccsds2110b6.model.TransferFrame frame = new com.leosatdata.ccsds2110b6.model.TransferFrame();
			frame.setQualityOfService(QualityOfService.EXPEDITED);
			frame.setPduTypeId(PduTypeId.SPDU);
			frame.setDfcId(DfcId.PACKETS);
			frame.setSourceOrDestination(SourceDestination.SOURCE);
			frame.setScid(localScid);
			frame.setPortId(0); // not used in P frames
			frame.setPcid(localPcid);
			frame.setDataField(payload);
			return frame;
		}
		if (pFrameVersion == 4) {
			com.leosatdata.ccsds7321b3.model.TransferFrame frame = new com.leosatdata.ccsds7321b3.model.TransferFrame();
			frame.setScid(localScid);
			frame.setSourceOrDestination(SourceDestination.SOURCE);
			frame.setVcid(0); // not clear
			frame.setMapid(0); // not clear
			frame.setHeaderTruncated(false);
			frame.setSequenceControl(QualityOfService.EXPEDITED);
			frame.setProtocolControl(PduTypeId.SPDU);
			frame.setOcf(false);
			frame.setDataField(payload);
			frame.setVcfCountLength(MAX_SEQUENCE_NUMBER_BYTES);
			return frame;
		}
		LOG.error("unknown p frame version: {}", pFrameVersion);
		return null;
	}

	// can be called on a separate thread, shouldn't share any state with encode
	public Object decode(byte[] data) {
		if (com.leosatdata.ccsds7320b4.util.TransferFrameParser.accept(data)) {
			if (uFrameVersion != 2) {
				return null;
			}
			try (BitInputStream bis = new BitInputStream(data)) {
				return framev2Parser.decode(bis);
			} catch (IOException e) {
				LOG.error("unable to read transferframe2", e);
				return null;
			}
		}
		if (com.leosatdata.ccsds2110b6.util.TransferFrameParser.accept(data)) {
			if (uFrameVersion != 3) {
				return null;
			}
			try (BitInputStream bis = new BitInputStream(data)) {
				return com.leosatdata.ccsds2110b6.util.TransferFrameParser.decode(bis);
			} catch (IOException e) {
				LOG.error("unable to read transferframe3", e);
				return null;
			}
		}
		if (com.leosatdata.ccsds7321b3.util.TransferFrameParser.accept(data)) {
			if (uFrameVersion != 4) {
				return null;
			}
			try (BitInputStream bis = new BitInputStream(data)) {
				return framev4Parser.decode(bis);
			} catch (IOException e) {
				LOG.error("unable to read transferframe4", e);
				return null;
			}
		}
		// data is somehow corrupted and completely unsupported even after crc and FEC
		LOG.debug("invalid frame received");
		return null;
	}

	private byte[] encode(Object obj) {
		ByteArrayOutputStream baos = new ByteArrayOutputStream();
		if (obj instanceof com.leosatdata.ccsds7320b4.model.TransferFrame) {
			com.leosatdata.ccsds7320b4.model.TransferFrame frame = (com.leosatdata.ccsds7320b4.model.TransferFrame) obj;
			try (BitOutputStream bos = new BitOutputStream(baos)) {
				framev2Parser.encode(frame, bos);
			} catch (IOException e1) {
				LOG.error("can't serialize transferframe2", e1);
				return null;
			}
			return baos.toByteArray();
		}
		if (obj instanceof com.leosatdata.ccsds2110b6.model.TransferFrame) {
			com.leosatdata.ccsds2110b6.model.TransferFrame frame = (com.leosatdata.ccsds2110b6.model.TransferFrame) obj;
			try (BitOutputStream bos = new BitOutputStream(baos)) {
				com.leosatdata.ccsds2110b6.util.TransferFrameParser.encode(frame, bos);
			} catch (IOException e1) {
				LOG.error("can't serialize transferframe3", e1);
				return null;
			}
			return baos.toByteArray();
		}
		if (obj instanceof com.leosatdata.ccsds7321b3.model.TransferFrame) {
			com.leosatdata.ccsds7321b3.model.TransferFrame frame = (com.leosatdata.ccsds7321b3.model.TransferFrame) obj;
			try (BitOutputStream bos = new BitOutputStream(baos)) {
				framev4Parser.encode(frame, bos);
			} catch (IOException e1) {
				LOG.error("can't serialize transferframe4", e1);
				return null;
			}
			return baos.toByteArray();
		}
		LOG.debug("invalid frame requested: {}", obj);
		return null;
	}

	public Object decodeSpdu(byte[] data) {
		try (BitInputStream bis = new BitInputStream(data)) {
			return spduParser.decode(bis);
		} catch (IOException e) {
			LOG.error("unable to read spdu", e);
			return null;
		}
	}

	public int getMaxDataLength() {
		if (uFrameVersion == 2) {
			return framev2Parser.getMaxDataLength();
		}
		if (uFrameVersion == 3) {
			return framev3Parser.getMaxDataLength();
		}
		if (uFrameVersion == 4) {
			return framev4Parser.getMaxDataLength();
		}
		return 0;
	}

	public byte[] assemblePacket(Object frame) {
		if (uFrameVersion == 2) {
			return framev2Parser.assemblePacket(frame);
		}
		if (uFrameVersion == 3) {
			return framev3Parser.assemblePacket(frame);
		}
		if (uFrameVersion == 4) {
			return framev4Parser.assemblePacket(frame);
		}
		return null;
	}

	private void scheduleWait(EventType type, long duration) {
		if (synchTimerTask != null) {
			synchTimerTask.cancel();
		}
		synchTimerTask = new TimerTask() {

			@Override
			public void run() {
				sendEvent(new Event(type));
			}
		};
		LOG.info("waiting: {} for {} ms", type, duration);
		synchTimer.schedule(synchTimerTask, duration);
	}

	private void sendEvent(Event e) {
		try {
			state.getQueue().put(e);
		} catch (InterruptedException e1) {
			Thread.currentThread().interrupt();
			LOG.error("can't send event", e1);
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
}
