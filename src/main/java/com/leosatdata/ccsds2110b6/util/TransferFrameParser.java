package com.leosatdata.ccsds2110b6.util;

import java.io.IOException;

import com.leosatdata.ccsds2110b6.model.DfcId;
import com.leosatdata.ccsds2110b6.model.PduTypeId;
import com.leosatdata.ccsds2110b6.model.QualityOfService;
import com.leosatdata.ccsds2110b6.model.SourceDestination;
import com.leosatdata.ccsds2110b6.model.TransferFrame;
import com.leosatdata.ccsds2110b6.model.TransferFrameHeader;
import com.leosatdata.util.BitInputStream;
import com.leosatdata.util.BitOutputStream;

public class TransferFrameParser {

	public static final int VERSION_NUMBER = 0b10;

	public static boolean accept(byte[] data) {
		if (data == null || data.length == 0) {
			return false;
		}
		return ((data[0] & 0b1100_0000) >> 6) == VERSION_NUMBER;
	}

	public static TransferFrame decode(BitInputStream bis) throws IOException {
		TransferFrameHeader header = new TransferFrameHeader();
		header.setVersionNumber(bis.readUnsignedInt(2));
		header.setQualityOfService(QualityOfService.valueOfCode(bis.readUnsignedInt(1)));
		header.setPduTypeId(PduTypeId.valueOfCode(bis.readUnsignedInt(1)));
		header.setDfcId(DfcId.valueOfCode(bis.readUnsignedInt(2)));
		header.setScid(bis.readUnsignedInt(10));
		header.setPcid(bis.readUnsignedInt(1));
		header.setPortId(bis.readUnsignedInt(3));
		header.setSourceOrDestination(SourceDestination.valueOfCode(bis.readUnsignedInt(1)));
		header.setFrameLength(bis.readUnsignedInt(11));
		header.setFrameSequenceNumber(bis.readUnsignedInt(8));
		TransferFrame result = new TransferFrame();
		result.setHeader(header);
		byte[] dataField = new byte[bis.available()];
		bis.readFully(dataField);
		result.setDataField(dataField);
		return result;
	}

	public static void encode(TransferFrame frame, BitOutputStream bos) throws IOException {
		TransferFrameHeader header = frame.getHeader();
		bos.writeUnsignedInt(2, header.getVersionNumber());
		bos.writeUnsignedInt(1, header.getQualityOfService().getCode());
		bos.writeUnsignedInt(1, header.getPduTypeId().getCode());
		bos.writeUnsignedInt(2, header.getDfcId().getCode());
		bos.writeUnsignedInt(10, header.getScid());
		bos.writeUnsignedInt(1, header.getPcid());
		bos.writeUnsignedInt(3, header.getPortId());
		bos.writeUnsignedInt(1, header.getSourceOrDestination().getCode());
		bos.writeUnsignedInt(11, header.getFrameLength());
		bos.writeUnsignedInt(8, header.getFrameSequenceNumber());
		bos.writeFully(frame.getDataField());
	}

}
