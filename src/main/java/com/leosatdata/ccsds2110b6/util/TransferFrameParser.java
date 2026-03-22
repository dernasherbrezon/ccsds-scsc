package com.leosatdata.ccsds2110b6.util;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import com.leosatdata.ccsds2110b6.model.DfcId;
import com.leosatdata.ccsds2110b6.model.PduTypeId;
import com.leosatdata.ccsds2110b6.model.QualityOfService;
import com.leosatdata.ccsds2110b6.model.SegmentFlags;
import com.leosatdata.ccsds2110b6.model.SourceDestination;
import com.leosatdata.ccsds2110b6.model.TransferFrame;
import com.leosatdata.util.BitInputStream;
import com.leosatdata.util.BitOutputStream;
import com.leosatdata.util.Configuration;

public class TransferFrameParser {

	private final int maxDataLength;
	private final int localScid;
	private final int localPcid;

	private int pseudoPacketId = 0;

	public TransferFrameParser(Configuration config) {
		this.localScid = config.getInteger("local_spacecraft_id", 0);
		this.localPcid = config.getInteger("local_pcid", 0);
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
		result.setScid(bis.readUnsignedInt(10));
		result.setPcid(bis.readUnsignedInt(1));
		result.setPortId(bis.readUnsignedInt(3));
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
		bos.writeUnsignedInt(10, frame.getScid());
		bos.writeUnsignedInt(1, frame.getPcid());
		bos.writeUnsignedInt(3, frame.getPortId());
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

	public List<TransferFrame> createUFrame(byte[] packet, QualityOfService qos, int portId) {
		if (packet.length < maxDataLength) {
			TransferFrame frame = new TransferFrame();
			frame.setQualityOfService(qos);
			frame.setPduTypeId(PduTypeId.USER_DATA);
			frame.setDfcId(DfcId.SEGMENTED); // support packet outputstream only
			frame.setSourceOrDestination(SourceDestination.SOURCE);
			frame.setScid(localScid);
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
			frame.setSourceOrDestination(SourceDestination.SOURCE);
			frame.setScid(localScid);
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

}
