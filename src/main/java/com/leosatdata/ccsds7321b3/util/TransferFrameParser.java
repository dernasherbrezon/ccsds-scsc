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

	// even if it dynamic, make it static and consistent with version 3 frame
	public static final int VERSION_NUMBER = 0b1100;
	public static final int VCF_COUNT_LENGTH = 0b011; // 3 bytes
	public static final int MAX_SEQUENCE_NUMBER = (int) Math.pow(2, VCF_COUNT_LENGTH);

	public static boolean accept(byte[] data) {
		if (data.length == 0) {
			return false;
		}
		return ((data[0] & 0b1111_0000) >> 4) == VERSION_NUMBER;
	}

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
				header.setVcfCount(bis.readUnsignedInt(header.getVcfCountLength() * 8));
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
		TransferFramePrimaryHeader header = frame.getHeader();
		bos.writeUnsignedInt(4, header.getVersionNumber());
		bos.writeUnsignedInt(16, header.getScid());
		bos.writeUnsignedInt(1, header.getSourceOrDestination().getCode());
		bos.writeUnsignedInt(6, header.getVcid());
		bos.writeUnsignedInt(4, header.getMapid());
		bos.writeBoolean(header.isHeaderTruncated());
		if (!header.isHeaderTruncated()) {
			bos.writeUnsignedInt(16, header.getFrameLength());
			bos.writeUnsignedInt(1, header.getSequenceControl().getCode());
			bos.writeUnsignedInt(1, header.getProtocolControl().getCode());
			bos.writeUnsignedInt(2, 0); // unused
			bos.writeBoolean(header.isOcf());
			bos.writeUnsignedInt(3, header.getVcfCountLength());
			if (header.getVcfCountLength() != 0b000) {
				bos.writeUnsignedInt(header.getVcfCountLength() * 8, header.getVcfCount());
			}
		}
		bos.writeFully(frame.getDataField());
	}

}
