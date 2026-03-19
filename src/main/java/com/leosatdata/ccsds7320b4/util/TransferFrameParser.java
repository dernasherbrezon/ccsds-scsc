package com.leosatdata.ccsds7320b4.util;

import java.io.IOException;

import com.leosatdata.ccsds7320b4.model.TransferFrame;
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
		if (data == null || data.length == 0) {
			return false;
		}
		return ((data[0] & 0b1100_0000) >> 6) == VERSION_NUMBER;
	}

	public TransferFrame decode(BitInputStream bis) throws IOException {
		TransferFrame result = new TransferFrame();
		result.setVersionNumber(bis.readUnsignedInt(2));
		result.setScid(bis.readUnsignedInt(8));
		result.setVcid(bis.readUnsignedInt(6));
		result.setVcFrameCount(bis.readUnsignedInt(24));
		result.setReplay(bis.readBoolean());
		result.setVcFrameCountCycleUsed(bis.readBoolean());
		bis.skipBits(2);
		if (result.isVcFrameCountCycleUsed()) {
			result.setVcFrameCountCycle(bis.readUnsignedInt(4));
		} else {
			bis.skipBits(4);
		}
		if (frameErrorControl) {
			result.setFrameErrorControl(bis.readUnsignedInt(16));
		}

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
		bos.writeUnsignedInt(2, VERSION_NUMBER);
		bos.writeUnsignedInt(8, frame.getScid());
		bos.writeUnsignedInt(6, frame.getVcid());
		bos.writeUnsignedInt(24, frame.getVcFrameCount());
		bos.writeBoolean(frame.isReplay());
		bos.writeBoolean(frame.isVcFrameCountCycleUsed());
		bos.writeUnsignedInt(2, 0); // unused
		if (frame.isVcFrameCountCycleUsed()) {
			bos.writeUnsignedInt(4, frame.getVcFrameCountCycle());
		} else {
			bos.writeUnsignedInt(4, 0);
		}
		if (frameErrorControl) {
			int frameErrorControl = 0;
			if (frame.getFrameErrorControl() != null) {
				frameErrorControl = frame.getFrameErrorControl();
			}
			// if not provided and required, then populate with zeros
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
