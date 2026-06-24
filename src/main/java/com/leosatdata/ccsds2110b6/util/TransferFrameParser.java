package com.leosatdata.ccsds2110b6.util;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.leosatdata.ccsds2110b6.model.DfcId;
import com.leosatdata.ccsds2110b6.model.PduTypeId;
import com.leosatdata.ccsds2110b6.model.QualityOfService;
import com.leosatdata.ccsds2110b6.model.SegmentFlags;
import com.leosatdata.ccsds2110b6.model.SourceDestination;
import com.leosatdata.ccsds2110b6.model.TransferFrame;
import com.leosatdata.service.UserDataCallback;
import com.leosatdata.util.BitInputStream;
import com.leosatdata.util.BitOutputStream;
import com.leosatdata.util.Configuration;
import com.leosatdata.util.Util;

public class TransferFrameParser {

	private final static Logger LOG = LoggerFactory.getLogger(TransferFrameParser.class);
	private final static int PORTID_LENGTH_BITS = 3;
	public final static int SPACECRAFT_LENGTH_BITS = 10;

	private final int maxDataLength;
	private final int localScid;
	private final int remoteScid;
	private final int localPcid;
	private final SourceDestination sourceDestination;

	private int pseudoPacketId = 0;

	private SegmentFlags previousFlag = SegmentFlags.LAST;
	private int currentPseudoPacketId = 0;
	private final ByteArrayOutputStream currentBuffer = new ByteArrayOutputStream();

	public TransferFrameParser(Configuration config) {
		this.localScid = config.getInteger("local_spacecraft_id", 0);
		this.localPcid = config.getInteger("local_pcid", 0);
		this.remoteScid = config.getInteger("remote_spacecraft_id", 0);
		this.sourceDestination = SourceDestination.valueOfCode(config.getInteger("source_destination_id", 0));
		int headerLength = TransferFrame.HEADER_LENGTH;
		headerLength += TransferFrame.SEGMENT_HEADER_LENGTH; // Uframes are segmented
		int maxFrameLength = config.getInteger("maximum_frame_length", 0);
		this.maxDataLength = maxFrameLength - headerLength;
		if (maxDataLength <= 0) {
			throw new IllegalArgumentException("maximum_frame_length is too small: " + maxFrameLength);
		}
	}

	public static boolean accept(byte[] data) {
		if (data == null || data.length == 0) {
			return false;
		}
		return ((data[0] & 0b1100_0000) >> 6) == TransferFrame.VERSION_NUMBER;
	}

	public static TransferFrame decode(BitInputStream bis) throws IOException {
		int start = bis.available();
		TransferFrame result = new TransferFrame();
		result.setVersionNumber(bis.readUnsignedInt(2));
		result.setQualityOfService(QualityOfService.valueOfCode(bis.readUnsignedInt(1)));
		result.setPduTypeId(PduTypeId.valueOfCode(bis.readUnsignedInt(1)));
		result.setDfcId(DfcId.valueOfCode(bis.readUnsignedInt(2)));
		result.setScid(bis.readUnsignedInt(SPACECRAFT_LENGTH_BITS));
		result.setPcid(bis.readUnsignedInt(1));
		result.setPortId(bis.readUnsignedInt(PORTID_LENGTH_BITS));
		result.setSourceOrDestination(SourceDestination.valueOfCode(bis.readUnsignedInt(1)));
		result.setFrameLength(bis.readUnsignedInt(11));
		result.setFrameSequenceNumber(bis.readUnsignedInt(8));
		if (result.getDfcId().equals(DfcId.SEGMENTED)) {
			result.setSegmentFlags(SegmentFlags.valueOfCode(bis.readUnsignedInt(2)));
			result.setPseudoPacketId(bis.readUnsignedInt(6));
		}
		int readSoFar = start - bis.available();
		// assume frame header is not truncated
		int payloadLength = result.getFrameLength() + 1 - readSoFar;
		byte[] dataField = new byte[payloadLength];
		bis.readFully(dataField);
		result.setDataField(dataField);
		return result;
	}

	public static void encode(TransferFrame frame, BitOutputStream bos) throws IOException {
		int headerLength = TransferFrame.HEADER_LENGTH;
		if (frame.getDfcId().equals(DfcId.SEGMENTED)) {
			headerLength++;
		}
		frame.setFrameLength(headerLength + frame.getDataField().length - 1);
		frame.setVersionNumber(TransferFrame.VERSION_NUMBER);
		bos.writeUnsignedInt(2, frame.getVersionNumber());
		bos.writeUnsignedInt(1, frame.getQualityOfService().getCode());
		bos.writeUnsignedInt(1, frame.getPduTypeId().getCode());
		bos.writeUnsignedInt(2, frame.getDfcId().getCode());
		bos.writeUnsignedInt(SPACECRAFT_LENGTH_BITS, frame.getScid());
		bos.writeUnsignedInt(1, frame.getPcid());
		bos.writeUnsignedInt(PORTID_LENGTH_BITS, frame.getPortId());
		bos.writeUnsignedInt(1, frame.getSourceOrDestination().getCode());
		bos.writeUnsignedInt(11, frame.getFrameLength());
		bos.writeUnsignedInt(8, frame.getFrameSequenceNumber());
		if (frame.getDfcId().equals(DfcId.SEGMENTED)) {
			bos.writeUnsignedInt(2, frame.getSegmentFlags().getCode());
			bos.writeUnsignedInt(6, frame.getPseudoPacketId());
		}
		bos.writeFully(frame.getDataField());
	}

	public int getMaxDataLength() {
		return maxDataLength;
	}

	public TransferFrame createPFrame(byte[] payload) {
		com.leosatdata.ccsds2110b6.model.TransferFrame frame = new com.leosatdata.ccsds2110b6.model.TransferFrame();
		frame.setQualityOfService(QualityOfService.EXPEDITED);
		frame.setPduTypeId(PduTypeId.SPDU);
		frame.setDfcId(DfcId.PACKETS);
		frame.setSourceOrDestination(sourceDestination);
		if (sourceDestination.equals(SourceDestination.SOURCE)) {
			frame.setScid(localScid);
		} else {
			frame.setScid(remoteScid);
		}
		frame.setPortId(0); // not used in P frames
		frame.setPcid(localPcid);
		frame.setDataField(payload);
		return frame;
	}

	public List<TransferFrame> createUFrame(byte[] packet, QualityOfService qos, int portId) {
		Util.validateInt(PORTID_LENGTH_BITS, "portId", portId);
		if (packet.length < maxDataLength) {
			TransferFrame frame = new TransferFrame();
			frame.setQualityOfService(qos);
			frame.setPduTypeId(PduTypeId.USER_DATA);
			frame.setDfcId(DfcId.SEGMENTED); // support packet outputstream only
			frame.setSourceOrDestination(sourceDestination);
			if (sourceDestination.equals(SourceDestination.SOURCE)) {
				frame.setScid(localScid);
			} else {
				frame.setScid(remoteScid);
			}
			frame.setPortId(portId);
			frame.setPcid(localPcid);
			frame.setDataField(packet);
			frame.setSegmentFlags(SegmentFlags.NO_SEGMENTATION);
			frame.setPseudoPacketId(pseudoPacketId);
			pseudoPacketId++;
			return Collections.singletonList(frame);
		}
		List<TransferFrame> result = new ArrayList<>();
		int toWrite = packet.length;
		int written = 0;
		int nextBatch = maxDataLength;
		SegmentFlags flags = SegmentFlags.FIRST;
		while (toWrite > 0) {
			byte[] buffer = new byte[nextBatch];
			System.arraycopy(packet, written, buffer, 0, buffer.length);

			TransferFrame frame = new TransferFrame();
			frame.setQualityOfService(qos);
			frame.setPduTypeId(PduTypeId.USER_DATA);
			frame.setDfcId(DfcId.SEGMENTED); // support packet outputstream only
			frame.setSourceOrDestination(sourceDestination);
			if (sourceDestination.equals(SourceDestination.SOURCE)) {
				frame.setScid(localScid);
			} else {
				frame.setScid(remoteScid);
			}
			frame.setPortId(portId);
			frame.setPcid(localPcid);
			frame.setDataField(buffer);
			frame.setSegmentFlags(flags);
			frame.setPseudoPacketId(pseudoPacketId);
			result.add(frame);

			toWrite -= nextBatch;
			written += nextBatch;
			if (toWrite > maxDataLength) {
				nextBatch = maxDataLength;
				flags = SegmentFlags.CONTINUING;
			} else {
				nextBatch = toWrite;
				flags = SegmentFlags.LAST;
			}
		}
		pseudoPacketId++;
		return result;
	}

	// first -> continuing -> last
	// first -> last
	public void onFrame(UserDataCallback callback, Object obj) {
		TransferFrame frame = (TransferFrame) obj;
		if (frame.getSegmentFlags().equals(SegmentFlags.NO_SEGMENTATION)) {
			callback.onPacketData(frame.getDataField(), frame.getPortId());
			return;
		}
		boolean transitionState = false;
		if (frame.getSegmentFlags().equals(SegmentFlags.FIRST)) {
			currentBuffer.reset();
			currentPseudoPacketId = frame.getPseudoPacketId();
			transitionState = true;
		}
		if (frame.getSegmentFlags().equals(SegmentFlags.LAST)) {
			transitionState = (previousFlag.equals(SegmentFlags.FIRST) || previousFlag.equals(SegmentFlags.CONTINUING));
		}
		if (frame.getSegmentFlags().equals(SegmentFlags.CONTINUING)) {
			transitionState = (previousFlag.equals(SegmentFlags.FIRST) || previousFlag.equals(SegmentFlags.CONTINUING));
		}

		if (!transitionState) {
			currentBuffer.reset();
			previousFlag = SegmentFlags.LAST;
			return;
		}

		if (currentPseudoPacketId != frame.getPseudoPacketId()) {
			LOG.info("invalid pseudo packet id received: {} expected {}", frame.getPseudoPacketId(), currentPseudoPacketId);
			currentBuffer.reset();
			previousFlag = SegmentFlags.LAST;
			return;
		}

		previousFlag = frame.getSegmentFlags();

		try {
			currentBuffer.write(frame.getDataField());
		} catch (IOException e) {
			LOG.error("can't output to bytes", e);
			currentBuffer.reset();
			previousFlag = SegmentFlags.LAST;
			return;
		}

		if (previousFlag.equals(SegmentFlags.LAST)) {
			byte[] result = currentBuffer.toByteArray();
			currentBuffer.reset();
			callback.onPacketData(result, frame.getPortId());
			return;
		}
	}

}
