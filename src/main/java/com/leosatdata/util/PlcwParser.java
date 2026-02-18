package com.leosatdata.util;

import java.io.IOException;

import com.leosatdata.model.Plcw;
import com.leosatdata.model.SpduHeader;

public class PlcwParser {

	public static Plcw decode(SpduHeader header, BitInputStream bis) throws IOException {
		Plcw message = new Plcw();
		message.setHeader(header);
		if (header.getType() == Plcw.SHORT_PLCW) {
			message.setRetransmit(bis.readBoolean());
			message.setPcid(bis.readUnsignedInt(1));
			bis.skipBits(1);
			message.setExpeditedFrameCounter(bis.readUnsignedInt(3));
			message.setReportValue(bis.readUnsignedInt(8));
		} else { // LONG_PLCW
			bis.skipBits(9);
			message.setRetransmit(bis.readBoolean());
			message.setPcid(bis.readUnsignedInt(1));
			message.setExpeditedFrameCounter(bis.readUnsignedInt(3));
			message.setReportValue(bis.readUnsignedInt(16));
		}
		return message;
	}

	public static void encode(Plcw message, BitOutputStream bos) throws IOException {
		if (message.getHeader().getType() == Plcw.SHORT_PLCW) {
			bos.writeBoolean(message.isRetransmit());
			bos.writeUnsignedInt(1, message.getPcid());
			bos.writeUnsignedInt(1, 0); // unused
			bos.writeUnsignedInt(3, message.getExpeditedFrameCounter());
			bos.writeUnsignedInt(8, message.getReportValue());
		} else {
			bos.writeUnsignedInt(9, 0); // unused
			bos.writeBoolean(message.isRetransmit());
			bos.writeUnsignedInt(1, message.getPcid());
			bos.writeUnsignedInt(3, message.getExpeditedFrameCounter());
			bos.writeUnsignedInt(16, message.getReportValue());
		}
	}

}
