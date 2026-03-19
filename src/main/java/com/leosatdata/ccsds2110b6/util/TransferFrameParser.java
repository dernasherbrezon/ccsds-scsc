package com.leosatdata.ccsds2110b6.util;

import java.io.IOException;

import com.leosatdata.ccsds2110b6.model.DfcId;
import com.leosatdata.ccsds2110b6.model.PduTypeId;
import com.leosatdata.ccsds2110b6.model.QualityOfService;
import com.leosatdata.ccsds2110b6.model.SourceDestination;
import com.leosatdata.ccsds2110b6.model.TransferFrame;
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
		byte[] dataField = new byte[bis.available()];
		bis.readFully(dataField);
		result.setDataField(dataField);
		return result;
	}

	public static void encode(TransferFrame frame, BitOutputStream bos) throws IOException {
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
		bos.writeFully(frame.getDataField());
	}

}
