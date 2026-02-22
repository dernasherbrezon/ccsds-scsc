package com.leosatdata.ccsds2110b6.util;

import java.io.IOException;

import com.leosatdata.ccsds2110b6.model.DfcId;
import com.leosatdata.ccsds2110b6.model.PduTypeId;
import com.leosatdata.ccsds2110b6.model.QualityOfService;
import com.leosatdata.ccsds2110b6.model.SourceDestination;
import com.leosatdata.ccsds2110b6.model.TransferFrame;
import com.leosatdata.ccsds2110b6.model.TransferFrameHeader;
import com.leosatdata.util.BitInputStream;

public class TransferFrameParser {

	public static TransferFrame parse(byte[] data) throws IOException {
		BitInputStream bis = new BitInputStream(data);
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

}
