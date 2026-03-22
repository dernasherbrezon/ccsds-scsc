package com.leosatdata.service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Collections;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.leosatdata.ccsds2110b6.model.DfcId;
import com.leosatdata.ccsds2110b6.model.PduTypeId;
import com.leosatdata.ccsds2110b6.model.QualityOfService;
import com.leosatdata.ccsds2110b6.model.SourceDestination;
import com.leosatdata.model.Event;
import com.leosatdata.model.Plcw;
import com.leosatdata.model.Spdu;
import com.leosatdata.model.State;
import com.leosatdata.util.BitInputStream;
import com.leosatdata.util.BitOutputStream;
import com.leosatdata.util.Configuration;
import com.leosatdata.util.SpduParser;
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

	private final com.leosatdata.ccsds7320b4.util.TransferFrameParser framev2Parser;
	private final com.leosatdata.ccsds2110b6.util.TransferFrameParser framev3Parser;
	private final com.leosatdata.ccsds7321b3.util.TransferFrameParser framev4Parser;
	private final SpduParser spduParser;

	public FrameSublayer(State state, Configuration config, Transceiver transceiver, NodeCallback callback) {
		this.state = state;
		this.config = config;
		this.transceiver = transceiver;
		this.uFrameVersion = config.getInteger("u_frame_version", -1);
		this.pFrameVersion = config.getInteger("frame_version", -1);
		this.spduVersion = config.getInteger("spdu_version", 1);
		this.localScid = config.getInteger("local_spacecraft_id", 0);
		this.localPcid = config.getInteger("local_pcid", 0);
		this.framev2Parser = new com.leosatdata.ccsds7320b4.util.TransferFrameParser(config);
		this.framev3Parser = new com.leosatdata.ccsds2110b6.util.TransferFrameParser(config);
		this.framev4Parser = new com.leosatdata.ccsds7321b3.util.TransferFrameParser(config);
		this.spduParser = new SpduParser(config);
		this.callback = callback;
	}

	public void handle(Event e) {
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
			frame = createPFrame(createPlcw(false));
		}

		if (frame == null) {
			frame = state.getExpQueue().poll();
			if (frame != null) {
				state.setuFrameLastSent(true);
			}
		}

		if (frame == null) {
			frame = state.getSeqQueue().poll();
			if (frame != null) {
				state.setuFrameLastSent(true);
			}
		}

		if (frame == null && !state.isuFrameLastSent() && state.isNeedPlcw()) {
			state.setNeedPlcw(false);
			state.setuFrameLastSent(false);
			frame = createPFrame(createPlcw(false));
		}

		if (frame == null) {
			return;
		}

		int nextSeqNumber;
		QualityOfService qos = TransferFrameUtil.getQualityOfService(frame);
		if (qos.equals(QualityOfService.EXPEDITED)) {
			nextSeqNumber = state.getVes();
		} else {
			nextSeqNumber = state.getSeqCtrlFsn();
		}
		if (nextSeqNumber > MAX_SEQUENCE_NUMBER) {
			nextSeqNumber = 0;
		} else {
			nextSeqNumber++;
		}

		TransferFrameUtil.setSequenceNumber(frame, nextSeqNumber);

		if (qos.equals(QualityOfService.EXPEDITED)) {
			// TODO notify obc about VE(S)
			state.setVes(nextSeqNumber);
		} else {
			// TODO notify obc about VS(S)
			state.setSeqCtrlFsn(nextSeqNumber);
		}

		debug(false, frame);

		byte[] payload = encode(frame);
		if (payload == null) {
			return;
		}

		transceiver.send(payload);
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
		if (obj instanceof com.leosatdata.ccsds7320b4.model.TransferFrame) {
			com.leosatdata.ccsds7320b4.model.TransferFrame frame = (com.leosatdata.ccsds7320b4.model.TransferFrame) obj;
			type = PduTypeId.USER_DATA;
			data = frame.getDataField();
			log.info("{}:{} /{}/{} {} bytes", new Object[] { frame.getScid(), frame.getVcid(), PduTypeId.USER_DATA, QualityOfService.EXPEDITED, frame.getDataField().length });
		}
		if (obj instanceof com.leosatdata.ccsds2110b6.model.TransferFrame) {
			com.leosatdata.ccsds2110b6.model.TransferFrame frame = (com.leosatdata.ccsds2110b6.model.TransferFrame) obj;
			type = frame.getPduTypeId();
			data = frame.getDataField();
			log.info("{}:{} /{}/{} {} bytes", new Object[] { frame.getScid(), frame.getPortId(), frame.getPduTypeId(), frame.getQualityOfService(), frame.getFrameLength() });
		}
		if (obj instanceof com.leosatdata.ccsds7321b3.model.TransferFrame) {
			com.leosatdata.ccsds7321b3.model.TransferFrame frame = (com.leosatdata.ccsds7321b3.model.TransferFrame) obj;
			type = frame.getProtocolControl();
			data = frame.getDataField();
			log.info("{}:{} /{}/{} {} bytes", new Object[] { frame.getScid(), frame.getVcid(), frame.getProtocolControl(), frame.getSequenceControl(), frame.getFrameLength() });
		}
		if (type != null && data != null && type.equals(PduTypeId.SPDU)) {
			log.info("\t{}", decodeSpdu(data));
		}
	}

	private byte[] createPlcw(boolean retransmitRequired) {
		Plcw plcw = new Plcw();
		plcw.setReportValue(state.getVr());
		plcw.setExpeditedFrameCounter(state.getVes());
		plcw.setPcid(localPcid);
		plcw.setRetransmit(retransmitRequired);
		ByteArrayOutputStream baos = new ByteArrayOutputStream();
		try (BitOutputStream bos = new BitOutputStream(baos)) {
			spduParser.encode(plcw, bos);
		} catch (IOException e1) {
			LOG.error("cannot encode spdu", e1);
			return null;
		}
		return baos.toByteArray();
	}

	@SuppressWarnings("rawtypes")
	public List createUFrame(byte[] payload, QualityOfService qos, int vcid) {
		if (uFrameVersion == 3) {
			return framev3Parser.createUFrame(payload, qos, vcid);
		}
		// FIXME other types
		return Collections.emptyList();
	}

//	public Object createUFrame(byte[] payload, QualityOfService qos, int vcid) {
//		// FIXME first header pointer
//		if (uFrameVersion == 2) {
//			com.leosatdata.ccsds7320b4.model.TransferFrame frame = new com.leosatdata.ccsds7320b4.model.TransferFrame();
//			frame.setReplay(false);
//			frame.setScid(localScid);
//			frame.setVcFrameCountCycleUsed(false);
//			frame.setVcid(vcid);
//			frame.setDataField(payload);
//			return frame;
//		}
//		if (uFrameVersion == 3) {
//			com.leosatdata.ccsds2110b6.model.TransferFrame frame = new com.leosatdata.ccsds2110b6.model.TransferFrame();
//			frame.setQualityOfService(qos);
//			frame.setPduTypeId(PduTypeId.USER_DATA);
//			frame.setDfcId(DfcId.PACKETS); // support packet outputstream only
//			frame.setSourceOrDestination(SourceDestination.SOURCE);
//			frame.setScid(localScid);
//			frame.setPortId(vcid);
//			frame.setPcid(localPcid);
//			frame.setDataField(payload);
//			return frame;
//		}
//		if (uFrameVersion == 4) {
//			com.leosatdata.ccsds7321b3.model.TransferFrame frame = new com.leosatdata.ccsds7321b3.model.TransferFrame();
//			frame.setScid(localScid);
//			frame.setSourceOrDestination(SourceDestination.SOURCE);
//			frame.setVcid(vcid);
//			frame.setMapid(0); // not clear
//			frame.setHeaderTruncated(false);
//			frame.setSequenceControl(qos);
//			frame.setProtocolControl(PduTypeId.USER_DATA);
//			frame.setOcf(false);
//			frame.setDataField(payload);
//			frame.setVcfCountLength(MAX_SEQUENCE_NUMBER_BYTES);
//			return frame;
//		}
//		return null;
//	}

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
}
