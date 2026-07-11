package com.leosatdata.ccsds7321b3.util;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.leosatdata.ccsds2110b6.model.PduTypeId;
import com.leosatdata.ccsds2110b6.model.QualityOfService;
import com.leosatdata.ccsds2110b6.model.SourceDestination;
import com.leosatdata.ccsds7321b3.model.ConstructionRules;
import com.leosatdata.ccsds7321b3.model.TransferFrame;
import com.leosatdata.service.UserDataCallback;
import com.leosatdata.util.BitInputStream;
import com.leosatdata.util.BitOutputStream;
import com.leosatdata.util.Configuration;
import com.leosatdata.util.Util;

public class TransferFrameParser {

	private final static Logger LOG = LoggerFactory.getLogger(TransferFrameParser.class);
	public final static int SPACECRAFT_LENGTH_BITS = 16;

	private final int insertZoneLength;
	private final boolean frameErrorControl;
	private final int maxDataLength;
	private final boolean fixedFrameLength;
	private final int localScid;
	private final int remoteScid;
	private final SourceDestination sourceDestination;
	private final int vcfCountLength;
	private final boolean usePcid;
	private final int vcidLengthBits;
	private final int localPcid;

	private int pseudoPacketId = 0;

	private ConstructionRules previousFlag = ConstructionRules.LAST_SEGMENT;
	private int currentPseudoPacketId = 0;
	private final ByteArrayOutputStream currentBuffer = new ByteArrayOutputStream();

	public TransferFrameParser(Configuration config) {
		this.localPcid = config.getInteger("local_pcid", 0);
		this.localScid = config.getInteger("local_spacecraft_id", 0);
		this.remoteScid = config.getInteger("remote_spacecraft_id", 0);
		this.sourceDestination = SourceDestination.valueOfCode(config.getInteger("source_destination_id", 0));
		this.insertZoneLength = config.getInteger("framev4.insertZoneLength", 0);
		this.frameErrorControl = config.getBoolean("framev4.frameErrorControl");
		this.fixedFrameLength = config.getBoolean("framev4.fixedLength");
		this.vcfCountLength = config.getInteger("maximum_frame_sequence_length", 0);
		int maxFrameLength = config.getInteger("maximum_frame_length", 0);
		int headerLength = 32 / 8;
		headerLength += 24 / 8; // not truncated header
		headerLength += vcfCountLength;
		headerLength += insertZoneLength;
		headerLength += 16; // segmented SDU
		headerLength += com.leosatdata.ccsds7321b3.model.TransferFrame.TFDF_HEADER_LENGTH;
		headerLength += 0; // no ocf
		headerLength += frameErrorControl ? com.leosatdata.ccsds7321b3.model.TransferFrame.FEC_LENGTH : 0;

		// spec:
		//
		// The MSB of the VCID field (bit 21) of the Version-4 Transfer Frame Primary Header shall
		// be used for the PCID in the Version-3 Frame header.
		//
		// commentary:
		//
		// v3 obviously is not v4. I guess some kind of wire compatibility is required
		// pcid is not defined in the TransferFrame structure
		int spduVersion = config.getInteger("spdu_version", 1);
		this.usePcid = (spduVersion == 1);
		if (this.usePcid) {
			this.vcidLengthBits = 5;
		} else {
			this.vcidLengthBits = 6;
		}

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
		result.setScid(bis.readUnsignedInt(SPACECRAFT_LENGTH_BITS));
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
		bos.writeUnsignedInt(SPACECRAFT_LENGTH_BITS, frame.getScid());
		bos.writeUnsignedInt(1, frame.getSourceOrDestination().getCode());
		if (usePcid) {
			bos.writeUnsignedInt(1, localPcid);
			bos.writeUnsignedInt(5, frame.getVcid());
		} else {
			bos.writeUnsignedInt(6, frame.getVcid());
		}
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

	public TransferFrame createPFrame(byte[] payload) {
		com.leosatdata.ccsds7321b3.model.TransferFrame frame = new com.leosatdata.ccsds7321b3.model.TransferFrame();
		frame.setSourceOrDestination(sourceDestination);
		if (sourceDestination.equals(SourceDestination.SOURCE)) {
			frame.setScid(localScid);
		} else {
			frame.setScid(remoteScid);
		}
		frame.setVcid(0); // not clear
		frame.setMapid(0); // not clear
		frame.setHeaderTruncated(false);
		frame.setSequenceControl(QualityOfService.EXPEDITED);
		frame.setProtocolControl(PduTypeId.SPDU);
		frame.setConstructionRule(ConstructionRules.NO_SEGMENTATION);
		frame.setOcf(false);
		frame.setDataField(payload);
		frame.setVcfCountLength(vcfCountLength);
		return frame;
	}

	public List<TransferFrame> createUFrame(byte[] packet, QualityOfService qos, int vcid) {
		int code = Util.validateInt(vcidLengthBits, "vcid", vcid);
		if (code != 0) {
			return Collections.emptyList();
		}
		if (packet.length < maxDataLength) {
			TransferFrame frame = new TransferFrame();
			frame.setSourceOrDestination(sourceDestination);
			if (sourceDestination.equals(SourceDestination.SOURCE)) {
				frame.setScid(localScid);
			} else {
				frame.setScid(remoteScid);
			}
			frame.setVcid(vcid);
			frame.setMapid(0);
			frame.setHeaderTruncated(false);
			frame.setSequenceControl(qos);
			frame.setProtocolControl(PduTypeId.USER_DATA);
			frame.setOcf(false);
			frame.setDataField(packet);
			frame.setVcfCountLength(vcfCountLength);
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
			frame.setSourceOrDestination(sourceDestination);
			if (sourceDestination.equals(SourceDestination.SOURCE)) {
				frame.setScid(localScid);
			} else {
				frame.setScid(remoteScid);
			}
			frame.setVcid(vcid);
			frame.setMapid(0);
			frame.setHeaderTruncated(false);
			frame.setSequenceControl(qos);
			frame.setProtocolControl(PduTypeId.USER_DATA);
			frame.setOcf(false);
			frame.setDataField(buffer);
			frame.setVcfCountLength(vcfCountLength);
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

	public void onFrame(UserDataCallback callback, Object obj) {
		TransferFrame frame = (TransferFrame) obj;
		if (frame.getConstructionRule().equals(ConstructionRules.NO_SEGMENTATION)) {
			callback.onPacketData(frame.getDataField(), frame.getMapid());
			return;
		}
		boolean transitionState = false;
		if (frame.getConstructionRule().equals(ConstructionRules.STARTING_SEGMENT)) {
			currentBuffer.reset();
			currentPseudoPacketId = frame.getUpid();
			transitionState = true;
		}
		if (frame.getConstructionRule().equals(ConstructionRules.LAST_SEGMENT)) {
			transitionState = (previousFlag.equals(ConstructionRules.STARTING_SEGMENT) || previousFlag.equals(ConstructionRules.CONTINUING_SEGMENT));
		}
		if (frame.getConstructionRule().equals(ConstructionRules.CONTINUING_SEGMENT)) {
			transitionState = (previousFlag.equals(ConstructionRules.STARTING_SEGMENT) || previousFlag.equals(ConstructionRules.CONTINUING_SEGMENT));
		}

		if (!transitionState) {
			currentBuffer.reset();
			previousFlag = ConstructionRules.LAST_SEGMENT;
			return;
		}

		if (currentPseudoPacketId != frame.getUpid()) {
			LOG.info("invalid pseudo packet id received: {} expected {}", frame.getUpid(), currentPseudoPacketId);
			currentBuffer.reset();
			previousFlag = ConstructionRules.LAST_SEGMENT;
			return;
		}

		previousFlag = frame.getConstructionRule();

		try {
			currentBuffer.write(frame.getDataField());
		} catch (IOException e) {
			LOG.error("can't output to bytes", e);
			currentBuffer.reset();
			previousFlag = ConstructionRules.LAST_SEGMENT;
			return;
		}

		if (previousFlag.equals(ConstructionRules.LAST_SEGMENT)) {
			byte[] result = currentBuffer.toByteArray();
			currentBuffer.reset();
			callback.onPacketData(result, frame.getMapid());
			return;
		}
	}

}
