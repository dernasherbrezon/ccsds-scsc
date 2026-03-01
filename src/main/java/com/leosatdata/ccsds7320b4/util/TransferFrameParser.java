package com.leosatdata.ccsds7320b4.util;

import java.io.IOException;

import com.leosatdata.ccsds7320b4.model.TransferFrame;
import com.leosatdata.ccsds7320b4.model.TransferFramePrimaryHeader;
import com.leosatdata.util.BitInputStream;
import com.leosatdata.util.BitOutputStream;
import com.leosatdata.util.Configuration;

public class TransferFrameParser {

	public static final int VERSION_NUMBER = 0b01;

	private final int insertZoneLength;
	private final boolean frameErrorControl;
	private final int dataFieldLength;

	public TransferFrameParser(Configuration config) {
		this.insertZoneLength = config.getInteger("aos.insertZoneLength", 0);
		this.frameErrorControl = config.getBoolean("aos.frameErrorControl");
		int headerLength = frameErrorControl ? 8 : 6;
		this.dataFieldLength = config.getInteger("maximum_frame_length", 0) - this.insertZoneLength - headerLength;
		if (dataFieldLength <= 0) {
			throw new IllegalArgumentException("invalid configuration: maximum_frame_length - aos.frameInsertZone");
		}
	}

	public static boolean accept(byte[] data) {
		if (data.length == 0) {
			return false;
		}
		return ((data[0] & 0b1100_0000) >> 6) == VERSION_NUMBER;
	}

	public TransferFrame decode(BitInputStream bis) throws IOException {
		TransferFramePrimaryHeader header = new TransferFramePrimaryHeader();
		header.setVersionNumber(bis.readUnsignedInt(2));
		header.setScid(bis.readUnsignedInt(8));
		header.setVcid(bis.readUnsignedInt(6));
		header.setVcFrameCount(bis.readUnsignedInt(24));
		header.setReplay(bis.readBoolean());
		header.setVcFrameCountCycleUsed(bis.readBoolean());
		bis.skipBits(2);
		if (header.isVcFrameCountCycleUsed()) {
			header.setVcFrameCountCycle(bis.readUnsignedInt(4));
		}
		if (frameErrorControl) {
			header.setFrameErrorControl(bis.readUnsignedInt(16));
		}

		TransferFrame result = new TransferFrame();
		result.setHeader(header);
		if (insertZoneLength != 0) {
			byte[] insertZone = new byte[insertZoneLength];
			bis.readFully(insertZone);
			result.setInsertZone(insertZone);
		}
		byte[] dataField = new byte[dataFieldLength];
		bis.readFully(dataField);
		result.setDataField(dataField);
		return result;
	}

	public void encode(TransferFrame frame, BitOutputStream bos) throws IOException {
		TransferFramePrimaryHeader header = frame.getHeader();
		bos.writeUnsignedInt(2, header.getVersionNumber());
		bos.writeUnsignedInt(8, header.getScid());
		bos.writeUnsignedInt(6, header.getVcid());
		bos.writeUnsignedInt(24, header.getVcFrameCount());
		bos.writeBoolean(header.isReplay());
		bos.writeBoolean(header.isVcFrameCountCycleUsed());
		bos.writeUnsignedInt(2, 0); // unused
		if (header.isVcFrameCountCycleUsed()) {
			bos.writeUnsignedInt(4, header.getVcFrameCountCycle());
		}
		if (frameErrorControl) {
			int frameErrorControl = 0;
			if (header.getFrameErrorControl() != null) {
				frameErrorControl = header.getFrameErrorControl();
			}
			bos.writeUnsignedInt(16, frameErrorControl);
		}
		if (insertZoneLength != 0) {
			byte[] insertZone = frame.getInsertZone();
			if (insertZone == null) {
				insertZone = new byte[insertZoneLength];
			}
			bos.writeFully(insertZone);
		}
		bos.writeFully(frame.getDataField());
	}

}
