package com.leosatdata.ccsds7321b3.util;

import java.io.IOException;

import com.leosatdata.ccsds2110b6.model.PduTypeId;
import com.leosatdata.ccsds2110b6.model.QualityOfService;
import com.leosatdata.ccsds2110b6.model.SourceDestination;
import com.leosatdata.ccsds7321b3.model.TransferFrame;
import com.leosatdata.ccsds7321b3.model.TransferFramePrimaryHeader;
import com.leosatdata.util.BitInputStream;
import com.leosatdata.util.BitOutputStream;
import com.leosatdata.util.Configuration;

public class TransferFrameParser {

	// even if it dynamic, make it static and consistent with version 3 frame
	public static final int VERSION_NUMBER = 0b1100;

	private final int insertZoneLength;
	private final boolean frameErrorControl;

	public TransferFrameParser(Configuration config) {
		this.insertZoneLength = config.getInteger("framev4.insertZoneLength", 0);
		this.frameErrorControl = config.getBoolean("framev4.frameErrorControl");
	}

	public static boolean accept(byte[] data) {
		if (data.length == 0) {
			return false;
		}
		return ((data[0] & 0b1111_0000) >> 4) == VERSION_NUMBER;
	}

	public TransferFrame decode(BitInputStream bis) throws IOException {
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

		byte[] insertZone = null;
		if (insertZoneLength != 0) {
			insertZone = new byte[insertZoneLength];
			bis.readFully(insertZone);
		}

		TransferFrame result = new TransferFrame();
		result.setHeader(header);
		result.setInsertZone(insertZone);
		// 4.1.5.2 or the Insert Zone, if the TFDF is not present,
		// FIXME how to calculate payload length? How come payload can be missing?
		// FIXME setup Transfer Frame Data Field Header
		byte[] dataField = new byte[bis.available()];
		bis.readFully(dataField);
		result.setDataField(dataField);
		if (header.isOcf()) {
			byte[] ocf = new byte[4];
			bis.readFully(ocf);
			result.setOperationalControl(ocf);
		}
		if (frameErrorControl) {
			byte[] fec = new byte[2];
			bis.readFully(fec);
			result.setFrameErrorControl(fec);
		}
		return result;
	}

	public void encode(TransferFrame frame, BitOutputStream bos) throws IOException {
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
		if (insertZoneLength != 0) {
			if (frame.getInsertZone() != null) {
				bos.writeFully(frame.getInsertZone());
			} else {
				// if missing just sent zeroes
				bos.writeFully(new byte[insertZoneLength]);
			}
		}
		bos.writeFully(frame.getDataField());
		if (header.isOcf()) {
			if (frame.getOperationalControl() != null) {
				bos.writeFully(frame.getOperationalControl());
			} else {
				bos.writeFully(new byte[4]);
			}
		}
		if (frameErrorControl) {
			if (frame.getFrameErrorControl() != null) {
				bos.writeFully(frame.getFrameErrorControl());
			} else {
				bos.writeFully(new byte[2]);
			}
		}
	}

}
