package com.leosatdata.ccsds7321b3.util;

import java.io.IOException;

import com.leosatdata.ccsds2110b6.model.PduTypeId;
import com.leosatdata.ccsds2110b6.model.QualityOfService;
import com.leosatdata.ccsds2110b6.model.SourceDestination;
import com.leosatdata.ccsds7321b3.model.TransferFrame;
import com.leosatdata.ccsds7321b3.model.TransferFramePrimaryHeader;
import com.leosatdata.util.BitInputStream;
import com.leosatdata.util.BitOutputStream;

public class TransferFrameParser {

	public static TransferFrame decode(BitInputStream bis) throws IOException {
		TransferFramePrimaryHeader header = new TransferFramePrimaryHeader();
		header.setVersionNumber(bis.readUnsignedInt(4));
		header.setScid(bis.readUnsignedInt(16));
		header.setSourceOrDestination(SourceDestination.valueOfCode(bis.readUnsignedInt(1)));
		header.setVcid(bis.readUnsignedInt(6));
		header.setMapid(bis.readUnsignedInt(4));
		header.setHeaderTruncated(bis.readBoolean());
		if (!header.isHeaderTruncated()) {
			header.setFrameLength(bis.readUnsignedInt(16));
			header.setSequenceControl(QualityOfService.valueOfCode(bis.readUnsignedInt(1)));
			header.setProtocolControl(PduTypeId.valueOfCode(bis.readUnsignedInt(1)));
			bis.skipBits(2);
			header.setOcf(bis.readBoolean());
			header.setVcfCountLength(bis.readUnsignedInt(3));
			if (header.getVcfCountLength() != 0b000) {
				header.setVcfCount(bis.readUnsignedInt(header.getVcfCountLength()));
			}
		}

		TransferFrame result = new TransferFrame();
		result.setHeader(header);
		byte[] dataField = new byte[bis.available()];
		bis.readFully(dataField);
		result.setDataField(dataField);
		return result;
	}

	public static void encode(TransferFrame frame, BitOutputStream bos) throws IOException {
		// FIXME
	}

}
