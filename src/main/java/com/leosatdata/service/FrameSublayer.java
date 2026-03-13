package com.leosatdata.service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

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
	private final int localScid;
	private final int localPcid;

	private final com.leosatdata.ccsds7320b4.util.TransferFrameParser framev2Parser;
	private final com.leosatdata.ccsds7321b3.util.TransferFrameParser framev4Parser;
	private final SpduParser spduParser;

	public FrameSublayer(State state, Configuration config, Transceiver transceiver, NodeCallback callback) {
		this.state = state;
		this.config = config;
		this.transceiver = transceiver;
		this.uFrameVersion = config.getInteger("u_frame_version", -1);
		this.pFrameVersion = config.getInteger("frame_version", -1);
		this.localScid = config.getInteger("local_spacecraft_id", 0);
		this.localPcid = config.getInteger("local_pcid", 0);
		this.framev2Parser = new com.leosatdata.ccsds7320b4.util.TransferFrameParser(config);
		this.framev4Parser = new com.leosatdata.ccsds7321b3.util.TransferFrameParser(config);
		this.spduParser = new SpduParser(config);
		this.callback = callback;
	}

	public void handle(Event e) {
		byte[] payload = null;
		QualityOfService qos = null;
		PduTypeId type = null;

		if (state.isPersistence()) {
			Spdu spdu = state.getMacQueue().poll();
			if (spdu == null) {
				return;
			}
			ByteArrayOutputStream baos = new ByteArrayOutputStream();
			try (BitOutputStream bos = new BitOutputStream(baos)) {
				spduParser.encode(spdu, bos);
				qos = QualityOfService.EXPEDITED;
				type = PduTypeId.SPDU;
			} catch (IOException e1) {
				LOG.error("cannot encode spdu", e);
				return;
			}
			payload = baos.toByteArray();
		}

		if (payload == null && state.isuFrameLastSent() && state.isNeedPlcw()) {
			state.setNeedPlcw(false);
			state.setuFrameLastSent(false);
			qos = QualityOfService.EXPEDITED;
			type = PduTypeId.SPDU;
			payload = createPlcw(false);
		}

		if (payload == null) {
			payload = state.getExpQueue().poll();
			if (payload != null) {
				qos = QualityOfService.EXPEDITED;
				type = PduTypeId.USER_DATA;
				state.setuFrameLastSent(true);
			}
		}

		if (payload == null) {
			payload = state.getSeqQueue().poll();
			if (payload != null) {
				qos = QualityOfService.SEQUENCE_CONTROLLED;
				type = PduTypeId.USER_DATA;
				state.setuFrameLastSent(true);
			}
		}

		if (payload == null && !state.isuFrameLastSent() && state.isNeedPlcw()) {
			state.setNeedPlcw(false);
			state.setuFrameLastSent(false);
			qos = QualityOfService.EXPEDITED;
			type = PduTypeId.SPDU;
			payload = createPlcw(false);
		}

		if (payload == null) {
			return;
		}

		byte[] frame = encode(payload, qos, type);
		if (frame == null) {
			return;
		}
		transceiver.send(frame);
	}

	private byte[] createPlcw(boolean retransmitRequired) {
		Plcw plcw = new Plcw();
		plcw.setReportValue(state.getVr());
		plcw.setExpeditedFrameCounter(state.getVes());
		plcw.setPcid(localPcid);
		plcw.setRetransmit(retransmitRequired);
		ByteArrayOutputStream baos = new ByteArrayOutputStream();
		TX.info(plcw.toString());
		try (BitOutputStream bos = new BitOutputStream(baos)) {
			spduParser.encode(plcw, bos);
		} catch (IOException e1) {
			LOG.error("cannot encode spdu", e1);
			return null;
		}
		return baos.toByteArray();
	}

	private byte[] encode(byte[] payload, QualityOfService qos, PduTypeId type) {
		if (type.equals(PduTypeId.SPDU)) {
			return encodePFrame(payload);
		} else {
			return encodeUFrame(payload, qos);
		}
	}

	private byte[] encodeUFrame(byte[] payload, QualityOfService qos) {
		ByteArrayOutputStream baos = new ByteArrayOutputStream();
		int nextSeqNumber;
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
		if (uFrameVersion == 2) {
			// FIXME this is not frame 2
			com.leosatdata.ccsds2110b6.model.TransferFrameHeader header = new com.leosatdata.ccsds2110b6.model.TransferFrameHeader();
			header.setVersionNumber(com.leosatdata.ccsds2110b6.util.TransferFrameParser.VERSION_NUMBER);
			header.setQualityOfService(qos);
			header.setPduTypeId(PduTypeId.USER_DATA);
			header.setDfcId(DfcId.USER); // FIXME check
			header.setScid(localScid);
			header.setPortId(0); // TODO read from user input?
			header.setSourceOrDestination(SourceDestination.SOURCE);
			header.setPcid(localPcid);
			header.setFrameLength(payload.length);
			header.setFrameSequenceNumber(nextSeqNumber);
			com.leosatdata.ccsds2110b6.model.TransferFrame frame = new com.leosatdata.ccsds2110b6.model.TransferFrame();
			frame.setHeader(header);
			frame.setDataField(payload);
			TX.info("{}:{} /{}/{} {} bytes", new Object[] { header.getScid(), header.getPortId(), header.getPduTypeId(), header.getQualityOfService(), header.getFrameLength() });
			try (BitOutputStream bos = new BitOutputStream(baos)) {
				com.leosatdata.ccsds2110b6.util.TransferFrameParser.encode(frame, bos);
			} catch (IOException e1) {
				LOG.error("can't serialize transferframe2", e1);
				return null;
			}
		}
		if (uFrameVersion == 3) {
			com.leosatdata.ccsds2110b6.model.TransferFrameHeader header = new com.leosatdata.ccsds2110b6.model.TransferFrameHeader();
			header.setVersionNumber(com.leosatdata.ccsds2110b6.util.TransferFrameParser.VERSION_NUMBER);
			header.setQualityOfService(qos);
			header.setPduTypeId(PduTypeId.USER_DATA);
			header.setDfcId(DfcId.USER); // FIXME check
			header.setSourceOrDestination(SourceDestination.SOURCE);
			header.setScid(localScid);
			header.setPortId(0); // TODO read from user input?
			header.setPcid(localPcid);
			header.setFrameLength(payload.length);
			header.setFrameSequenceNumber(nextSeqNumber);
			com.leosatdata.ccsds2110b6.model.TransferFrame frame = new com.leosatdata.ccsds2110b6.model.TransferFrame();
			frame.setHeader(header);
			frame.setDataField(payload);
			TX.info("{}:{} /{}/{} {} bytes", new Object[] { header.getScid(), header.getPortId(), header.getPduTypeId(), header.getQualityOfService(), header.getFrameLength() });
			try (BitOutputStream bos = new BitOutputStream(baos)) {
				com.leosatdata.ccsds2110b6.util.TransferFrameParser.encode(frame, bos);
			} catch (IOException e1) {
				LOG.error("can't serialize transferframe3", e1);
				return null;
			}
		}
		if (uFrameVersion == 4) {
			com.leosatdata.ccsds7321b3.model.TransferFramePrimaryHeader header = new com.leosatdata.ccsds7321b3.model.TransferFramePrimaryHeader();
			header.setVersionNumber(com.leosatdata.ccsds7321b3.util.TransferFrameParser.VERSION_NUMBER);
			header.setScid(localScid);
			header.setSourceOrDestination(SourceDestination.SOURCE);
			header.setVcid(0); // TODO read from user input?
			header.setMapid(0); // not clear
			header.setHeaderTruncated(false);
			header.setFrameLength(payload.length);
			header.setSequenceControl(qos);
			header.setProtocolControl(PduTypeId.USER_DATA);
			header.setOcf(false);
			com.leosatdata.ccsds7321b3.model.TransferFrame frame = new com.leosatdata.ccsds7321b3.model.TransferFrame();
			frame.setHeader(header);
			frame.setDataField(payload);
			frame.getHeader().setVcfCountLength(MAX_SEQUENCE_NUMBER_BYTES);
			frame.getHeader().setVcfCount(nextSeqNumber);
			TX.info("{}:{} /{}/{} {} bytes", new Object[] { header.getScid(), header.getVcid(), header.getProtocolControl(), header.getSequenceControl(), header.getFrameLength() });
			try (BitOutputStream bos = new BitOutputStream(baos)) {
				framev4Parser.encode(frame, bos);
			} catch (IOException e1) {
				LOG.error("can't serialize transferframe4", e1);
				return null;
			}
		}

		if (qos.equals(QualityOfService.EXPEDITED)) {
			// TODO notify obc about VE(S)
			state.setVes(nextSeqNumber);
		} else {
			// TODO notify obc about VS(S)
			state.setSeqCtrlFsn(nextSeqNumber);
		}
		return baos.toByteArray();
	}

	private byte[] encodePFrame(byte[] payload) {
		ByteArrayOutputStream baos = new ByteArrayOutputStream();
		int nextSeqNumber = state.getVes();
		if (nextSeqNumber > MAX_SEQUENCE_NUMBER) {
			nextSeqNumber = 0;
		} else {
			nextSeqNumber++;
		}
		if (pFrameVersion == 3) {
			com.leosatdata.ccsds2110b6.model.TransferFrameHeader header = new com.leosatdata.ccsds2110b6.model.TransferFrameHeader();
			header.setVersionNumber(com.leosatdata.ccsds2110b6.util.TransferFrameParser.VERSION_NUMBER);
			header.setQualityOfService(QualityOfService.EXPEDITED);
			header.setPduTypeId(PduTypeId.SPDU);
			header.setDfcId(DfcId.PACKETS);
			header.setSourceOrDestination(SourceDestination.SOURCE);
			header.setScid(localScid);
			header.setPortId(0); // not used in P frames
			header.setPcid(localPcid);
			header.setFrameLength(payload.length);
			header.setFrameSequenceNumber(nextSeqNumber);
			com.leosatdata.ccsds2110b6.model.TransferFrame frame = new com.leosatdata.ccsds2110b6.model.TransferFrame();
			frame.setHeader(header);
			frame.setDataField(payload);
			TX.info("{}:{} /{}/{} {} bytes", new Object[] { header.getScid(), header.getPortId(), header.getPduTypeId(), header.getQualityOfService(), header.getFrameLength() });
			try (BitOutputStream bos = new BitOutputStream(baos)) {
				com.leosatdata.ccsds2110b6.util.TransferFrameParser.encode(frame, bos);
			} catch (IOException e1) {
				LOG.error("can't serialize transferframe3", e1);
				return null;
			}
		} else if (pFrameVersion == 4) {
			com.leosatdata.ccsds7321b3.model.TransferFramePrimaryHeader header = new com.leosatdata.ccsds7321b3.model.TransferFramePrimaryHeader();
			header.setVersionNumber(com.leosatdata.ccsds7321b3.util.TransferFrameParser.VERSION_NUMBER);
			header.setScid(localScid);
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
			frame.getHeader().setVcfCountLength(MAX_SEQUENCE_NUMBER_BYTES);
			frame.getHeader().setVcfCount(nextSeqNumber);
			TX.info("{}:{} /{}/{} {} bytes", new Object[] { header.getScid(), header.getVcid(), header.getProtocolControl(), header.getSequenceControl(), header.getFrameLength() });
			try (BitOutputStream bos = new BitOutputStream(baos)) {
				framev4Parser.encode(frame, bos);
			} catch (IOException e1) {
				LOG.error("can't serialize transferframe4", e1);
				return null;
			}
		} else {
			LOG.error("unknown p frame version: {}", pFrameVersion);
			return null;
		}

		// TODO notify obc about VE(S)
		state.setVes(nextSeqNumber);
		return baos.toByteArray();
	}

	// can be called on a separate thread, shouldn't share any state with encode
	public Object decode(byte[] data) {
		if (com.leosatdata.ccsds7320b4.util.TransferFrameParser.accept(data)) {
			if (uFrameVersion != 2) {
				return null;
			}
			try (BitInputStream bis = new BitInputStream(data)) {
				com.leosatdata.ccsds7320b4.model.TransferFrame result = framev2Parser.decode(bis);
				com.leosatdata.ccsds7320b4.model.TransferFramePrimaryHeader header = result.getHeader();
				RX.info("{}:{} /{}/{} {} bytes", new Object[] { header.getScid(), header.getVcid(), PduTypeId.USER_DATA, QualityOfService.EXPEDITED, result.getDataField().length });
				return result;
			} catch (IOException e) {
				LOG.error("unable to read transferframe2", e);
				return null;
			}
		} else if (com.leosatdata.ccsds2110b6.util.TransferFrameParser.accept(data)) {
			if (uFrameVersion != 3) {
				return null;
			}
			try (BitInputStream bis = new BitInputStream(data)) {
				com.leosatdata.ccsds2110b6.model.TransferFrame result = com.leosatdata.ccsds2110b6.util.TransferFrameParser.decode(bis);
				com.leosatdata.ccsds2110b6.model.TransferFrameHeader header = result.getHeader();
				RX.info("{}:{} /{}/{} {} bytes", new Object[] { header.getScid(), header.getPortId(), header.getPduTypeId(), header.getQualityOfService(), header.getFrameLength() });
				return result;
			} catch (IOException e) {
				LOG.error("unable to read transferframe3", e);
				return null;
			}
		} else if (com.leosatdata.ccsds7321b3.util.TransferFrameParser.accept(data)) {
			if (uFrameVersion != 4) {
				return null;
			}
			try (BitInputStream bis = new BitInputStream(data)) {
				com.leosatdata.ccsds7321b3.model.TransferFrame result = framev4Parser.decode(bis);
				com.leosatdata.ccsds7321b3.model.TransferFramePrimaryHeader header = result.getHeader();
				RX.info("{}:{} /{}/{} {} bytes", new Object[] { header.getScid(), header.getVcid(), header.getProtocolControl(), header.getSequenceControl(), header.getFrameLength() });
				return result;
			} catch (IOException e) {
				LOG.error("unable to read transferframe4", e);
				return null;
			}
		} else {
			// data is somehow corrupted and completely unsupported even after crc and FEC
			LOG.debug("invalid frame received");
			return null;
		}
	}

	public Object decodeSpdu(byte[] data) {
		try (BitInputStream bis = new BitInputStream(data)) {
			Object result = spduParser.decode(bis);
			RX.info(result.toString());
			return result;
		} catch (IOException e) {
			LOG.error("unable to read spdu", e);
			return null;
		}
	}

	public static PduTypeId getPduTypeId(Object obj) {
		if (obj instanceof com.leosatdata.ccsds7320b4.model.TransferFrame) {
			return PduTypeId.USER_DATA;
		}
		if (obj instanceof com.leosatdata.ccsds2110b6.model.TransferFrame) {
			com.leosatdata.ccsds2110b6.model.TransferFrame frame = (com.leosatdata.ccsds2110b6.model.TransferFrame) obj;
			return frame.getHeader().getPduTypeId();
		}
		if (obj instanceof com.leosatdata.ccsds7321b3.model.TransferFrame) {
			com.leosatdata.ccsds7321b3.model.TransferFrame frame = (com.leosatdata.ccsds7321b3.model.TransferFrame) obj;
			return frame.getHeader().getProtocolControl();
		}
		return null;
	}

	public static SourceDestination getSourceDestination(Object obj) {
		if (obj instanceof com.leosatdata.ccsds7320b4.model.TransferFrame) {
			return SourceDestination.UNKNOWN;
		}
		if (obj instanceof com.leosatdata.ccsds2110b6.model.TransferFrame) {
			com.leosatdata.ccsds2110b6.model.TransferFrame frame = (com.leosatdata.ccsds2110b6.model.TransferFrame) obj;
			return frame.getHeader().getSourceOrDestination();
		}
		if (obj instanceof com.leosatdata.ccsds7321b3.model.TransferFrame) {
			com.leosatdata.ccsds7321b3.model.TransferFrame frame = (com.leosatdata.ccsds7321b3.model.TransferFrame) obj;
			return frame.getHeader().getSourceOrDestination();
		}
		return null;
	}

	public static int getScid(Object obj) {
		if (obj instanceof com.leosatdata.ccsds7320b4.model.TransferFrame) {
			com.leosatdata.ccsds7320b4.model.TransferFrame frame = (com.leosatdata.ccsds7320b4.model.TransferFrame) obj;
			return frame.getHeader().getScid();
		}
		if (obj instanceof com.leosatdata.ccsds2110b6.model.TransferFrame) {
			com.leosatdata.ccsds2110b6.model.TransferFrame frame = (com.leosatdata.ccsds2110b6.model.TransferFrame) obj;
			return frame.getHeader().getScid();
		}
		if (obj instanceof com.leosatdata.ccsds7321b3.model.TransferFrame) {
			com.leosatdata.ccsds7321b3.model.TransferFrame frame = (com.leosatdata.ccsds7321b3.model.TransferFrame) obj;
			return frame.getHeader().getScid();
		}
		return -1;
	}

	public static byte[] getPayload(Object obj) {
		if (obj instanceof com.leosatdata.ccsds7320b4.model.TransferFrame) {
			com.leosatdata.ccsds7320b4.model.TransferFrame frame = (com.leosatdata.ccsds7320b4.model.TransferFrame) obj;
			return frame.getDataField();
		}
		if (obj instanceof com.leosatdata.ccsds2110b6.model.TransferFrame) {
			com.leosatdata.ccsds2110b6.model.TransferFrame frame = (com.leosatdata.ccsds2110b6.model.TransferFrame) obj;
			return frame.getDataField();
		}
		if (obj instanceof com.leosatdata.ccsds7321b3.model.TransferFrame) {
			com.leosatdata.ccsds7321b3.model.TransferFrame frame = (com.leosatdata.ccsds7321b3.model.TransferFrame) obj;
			return frame.getDataField();
		}
		return null;
	}

}
