package com.leosatdata.ccsds2110b6.util;

import java.io.IOException;

import com.leosatdata.ccsds2110b6.model.DfcId;
import com.leosatdata.ccsds2110b6.model.PduTypeId;
import com.leosatdata.ccsds2110b6.model.QualityOfService;
import com.leosatdata.ccsds2110b6.model.SegmentFlags;
import com.leosatdata.ccsds2110b6.model.SourceDestination;
import com.leosatdata.ccsds2110b6.model.TransferFrame;
import com.leosatdata.util.BitInputStream;
import com.leosatdata.util.BitOutputStream;

public class TransferFrameParser {

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

}
