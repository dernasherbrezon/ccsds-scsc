package com.leosatdata.ccsds7321b3.util;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import com.leosatdata.ccsds2110b6.model.PduTypeId;
import com.leosatdata.ccsds2110b6.model.QualityOfService;
import com.leosatdata.ccsds2110b6.model.SourceDestination;
import com.leosatdata.ccsds7321b3.model.ConstructionRules;
import com.leosatdata.ccsds7321b3.model.TransferFrame;
import com.leosatdata.util.BitInputStream;
import com.leosatdata.util.BitOutputStream;
import com.leosatdata.util.Configuration;

public class TransferFrameParser {

	private final int insertZoneLength;
	private final boolean frameErrorControl;
	private final int maxDataLength;
	private final boolean fixedFrameLength;
	private final int localScid;

	private int pseudoPacketId = 0;

	public TransferFrameParser(Configuration config) {
		this.localScid = config.getInteger("local_spacecraft_id", 0);
		this.insertZoneLength = config.getInteger("framev4.insertZoneLength", 0);
		this.frameErrorControl = config.getBoolean("framev4.frameErrorControl");
		this.fixedFrameLength = config.getBoolean("framev4.fixedLength");
		int maxFrameLength = config.getInteger("maximum_frame_length", 0);
		int headerLength = 32 / 8;
		headerLength += 24 / 8; // not truncated header
		headerLength += 1; // VcfCountLength=1 byte
		headerLength += insertZoneLength;
		headerLength += 16; // segmented SDU
		headerLength += com.leosatdata.ccsds7321b3.model.TransferFrame.TFDF_HEADER_LENGTH;
		headerLength += 0; // no ocf
		headerLength += frameErrorControl ? com.leosatdata.ccsds7321b3.model.TransferFrame.FEC_LENGTH : 0;

		this.maxDataLength = maxFrameLength - headerLength;
		if (maxDataLength <= 0) {
			throw new IllegalArgumentException("maximum_frame_length is too small: " + maxFrameLength);
		}
	}

	public static boolean accept(byte[] data) {
		if (data == null || data.length == 0) {
			return false;
		}
		return ((data[0] & 0b1111_0000) >> 4) == TransferFrame.VERSION_NUMBER;
	}

	public TransferFrame decode(BitInputStream bis) throws IOException {
		int start = bis.available();
		TransferFrame result = new TransferFrame();
		result.setVersionNumber(bis.readUnsignedInt(4));
		result.setScid(bis.readUnsignedInt(16));
		result.setSourceOrDestination(SourceDestination.valueOfCode(bis.readUnsignedInt(1)));
		result.setVcid(bis.readUnsignedInt(6));
		result.setMapid(bis.readUnsignedInt(4));
		result.setHeaderTruncated(bis.readBoolean());
		if (!result.isHeaderTruncated()) {
			result.setFrameLength(bis.readUnsignedInt(16));
			result.setSequenceControl(QualityOfService.valueOfCode(bis.readUnsignedInt(1)));
			result.setProtocolControl(PduTypeId.valueOfCode(bis.readUnsignedInt(1)));
			bis.skipBits(2);
			result.setOcf(bis.readBoolean());
			result.setVcfCountLength(bis.readUnsignedInt(3));
			if (result.getVcfCountLength() != 0b000) {
				result.setVcfCount(bis.readUnsignedInt(result.getVcfCountLength() * 8));
			}
		}

		byte[] insertZone = null;
		if (insertZoneLength != 0) {
			insertZone = new byte[insertZoneLength];
			bis.readFully(insertZone);
		}

		result.setInsertZone(insertZone);

		result.setConstructionRule(ConstructionRules.valueOfCode(bis.readUnsignedInt(3)));
		result.setUpid(bis.readUnsignedInt(5));
		if (hasFirstHeaderPointer(result)) {
			result.setFirstHeaderPointer(bis.readUnsignedInt(16));
		}
		int readSoFar = start - bis.available();
		// assume frame header is not truncated
		int payloadLength = result.getFrameLength() + 1 - readSoFar;
		if (result.isOcf()) {
			payloadLength -= TransferFrame.OCF_LENGTH;
		}
		if (frameErrorControl) {
			payloadLength -= TransferFrame.FEC_LENGTH;
		}
		byte[] dataField = new byte[payloadLength];
		bis.readFully(dataField);
		result.setDataField(dataField);
		if (result.isOcf()) {
			byte[] ocf = new byte[TransferFrame.OCF_LENGTH];
			bis.readFully(ocf);
			result.setOperationalControl(ocf);
		}
		if (frameErrorControl) {
			byte[] fec = new byte[TransferFrame.FEC_LENGTH];
			bis.readFully(fec);
			result.setFrameErrorControl(fec);
		}
		return result;
	}

	public void encode(TransferFrame frame, BitOutputStream bos) throws IOException {
		int frameLength = 32 / 8;
		if (!frame.isHeaderTruncated()) {
			frameLength += 24 / 8;
			if (frame.getVcfCountLength() != 0b000) {
				frameLength += frame.getVcfCountLength();
			}
		}
		frameLength += insertZoneLength;
		if (hasFirstHeaderPointer(frame)) {
			frameLength += 16 / 8;
		}
		frameLength += TransferFrame.TFDF_HEADER_LENGTH;
		frameLength += frame.isOcf() ? TransferFrame.OCF_LENGTH : 0;
		frameLength += frameErrorControl ? TransferFrame.FEC_LENGTH : 0;
		if (fixedFrameLength) {
			frameLength += maxDataLength;
		} else {
			frameLength += frame.getDataField().length;
		}
		frame.setFrameLength(frameLength - 1);
		frame.setVersionNumber(TransferFrame.VERSION_NUMBER);
		bos.writeUnsignedInt(4, frame.getVersionNumber());
		bos.writeUnsignedInt(16, frame.getScid());
		bos.writeUnsignedInt(1, frame.getSourceOrDestination().getCode());
		bos.writeUnsignedInt(6, frame.getVcid());
		bos.writeUnsignedInt(4, frame.getMapid());
		bos.writeBoolean(frame.isHeaderTruncated());
		if (!frame.isHeaderTruncated()) {
			bos.writeUnsignedInt(16, frame.getFrameLength());
			bos.writeUnsignedInt(1, frame.getSequenceControl().getCode());
			bos.writeUnsignedInt(1, frame.getProtocolControl().getCode());
			bos.writeUnsignedInt(2, 0); // unused
			bos.writeBoolean(frame.isOcf());
			bos.writeUnsignedInt(3, frame.getVcfCountLength());
			if (frame.getVcfCountLength() != 0b000) {
				bos.writeUnsignedInt(frame.getVcfCountLength() * 8, frame.getVcfCount());
			}
		}
		if (insertZoneLength != 0) {
			if (frame.getInsertZone() != null) {
				bos.writeFully(frame.getInsertZone());
			} else {
				// if missing just sent zeroes
				bos.writeFully(new byte[insertZoneLength]);
			}
		}
		bos.writeUnsignedInt(3, frame.getConstructionRule().getCode());
		bos.writeUnsignedInt(5, frame.getUpid());
		if (hasFirstHeaderPointer(frame)) {
			int firstHeaderPointer = 0;
			if (frame.getFirstHeaderPointer() != null) {
				firstHeaderPointer = frame.getFirstHeaderPointer();
			}
			bos.writeUnsignedInt(16, firstHeaderPointer);
		}
		bos.writeFully(frame.getDataField());
		if (fixedFrameLength) {
			int padding = maxDataLength - frame.getDataField().length;
			if (padding > 0) {
				bos.writeFully(new byte[padding]);
			}
		}
		if (frame.isOcf()) {
			if (frame.getOperationalControl() != null) {
				bos.writeFully(frame.getOperationalControl());
			} else {
				bos.writeFully(new byte[TransferFrame.OCF_LENGTH]);
			}
		}
		if (frameErrorControl) {
			if (frame.getFrameErrorControl() != null) {
				bos.writeFully(frame.getFrameErrorControl());
			} else {
				bos.writeFully(new byte[TransferFrame.FEC_LENGTH]);
			}
		}
	}

	private static boolean hasFirstHeaderPointer(TransferFrame frame) {
		return frame.getConstructionRule().equals(ConstructionRules.PSMF) || frame.getConstructionRule().equals(ConstructionRules.START_VCDU) || frame.getConstructionRule().equals(ConstructionRules.CONTINUING_VCDU);
	}

	public int getMaxDataLength() {
		return maxDataLength;
	}

	public List<TransferFrame> createUFrame(byte[] packet, QualityOfService qos, int portId) {
		if (packet.length < maxDataLength) {
			TransferFrame frame = new TransferFrame();
			frame.setScid(localScid);
			frame.setSourceOrDestination(SourceDestination.SOURCE);
			frame.setVcid(0); // TODO bit 21 of vcid is localPcid
			frame.setMapid(portId);
			frame.setHeaderTruncated(false);
			frame.setSequenceControl(qos);
			frame.setProtocolControl(PduTypeId.USER_DATA);
			frame.setOcf(false);
			frame.setDataField(packet);
			frame.setVcfCountLength(1);
			frame.setConstructionRule(ConstructionRules.NO_SEGMENTATION);
			frame.setUpid(pseudoPacketId);
			pseudoPacketId++;
			return Collections.singletonList(frame);
		}
		List<TransferFrame> result = new ArrayList<>();
		int toWrite = packet.length;
		int written = 0;
		int nextBatch = maxDataLength;
		ConstructionRules flags = ConstructionRules.STARTING_SEGMENT;
		while (toWrite > 0) {
			byte[] buffer = new byte[nextBatch];
			System.arraycopy(packet, written, buffer, 0, buffer.length);

			TransferFrame frame = new TransferFrame();
			frame.setScid(localScid);
			frame.setSourceOrDestination(SourceDestination.SOURCE);
			frame.setVcid(0); // TODO bit 21 of vcid is localPcid
			frame.setMapid(portId);
			frame.setHeaderTruncated(false);
			frame.setSequenceControl(qos);
			frame.setProtocolControl(PduTypeId.USER_DATA);
			frame.setOcf(false);
			frame.setDataField(buffer);
			frame.setVcfCountLength(1);
			frame.setConstructionRule(flags);
			frame.setUpid(pseudoPacketId);
			result.add(frame);

			toWrite -= nextBatch;
			written += nextBatch;
			if (toWrite > maxDataLength) {
				nextBatch = maxDataLength;
				flags = ConstructionRules.CONTINUING_SEGMENT;
			} else {
				nextBatch = toWrite;
				flags = ConstructionRules.LAST_SEGMENT;
			}
		}
		pseudoPacketId++;
		return result;
	}

	public byte[] assemblePacket(Object frame) {
		// TODO Auto-generated method stub
		return null;
	}

}
