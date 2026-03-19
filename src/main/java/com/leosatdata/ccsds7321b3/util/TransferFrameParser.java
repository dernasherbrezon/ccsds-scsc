package com.leosatdata.ccsds7321b3.util;

import java.io.IOException;

import com.leosatdata.ccsds2110b6.model.PduTypeId;
import com.leosatdata.ccsds2110b6.model.QualityOfService;
import com.leosatdata.ccsds2110b6.model.SourceDestination;
import com.leosatdata.ccsds7321b3.model.TransferFrame;
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
		// 4.1.5.2 or the Insert Zone, if the TFDF is not present,
		// FIXME how to calculate payload length? How come payload can be missing?
		// FIXME setup Transfer Frame Data Field frame
		byte[] dataField = new byte[bis.available()];
		bis.readFully(dataField);
		result.setDataField(dataField);
		if (result.isOcf()) {
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
		bos.writeFully(frame.getDataField());
		if (frame.isOcf()) {
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
