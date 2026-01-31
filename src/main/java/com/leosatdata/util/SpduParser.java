package com.leosatdata.util;

import java.io.IOException;

import com.leosatdata.model.DataEncoding;
import com.leosatdata.model.DataRate;
import com.leosatdata.model.Frequency;
import com.leosatdata.model.Modulation;
import com.leosatdata.model.Plcw;
import com.leosatdata.model.TransmitterParameters;
import com.leosatdata.model.SpduHeader;
import com.leosatdata.model.TransmitterMode;

public class SpduParser {

	private final static int FIXED_SPDU = 1;
	private final static int VARIABLE_SPDU = 0;

	private final static int SHORT_PLCW = 0; // Type F1
	private final static int LONG_PLCW = 1; // Type F2

	private final static int TYPE1_DIRECTIVE = 0b000;

	private final static int SET_TRANSMITTER_PARAMETERS = 0b000;
	private final static int SET_CONTROL_PARAMETERS = 0b001;
	private final static int SET_RECEIVED_PARAMETERS = 0b010;
	private final static int SET_VR = 0b011;
	private final static int REPORT_REQUEST = 0b100;
	private final static int SET_PL_EXTENSIONS = 0b110;
	private final static int REPORT_SOURCE_SCID = 0b111;

	private final SpduCallback callback;

	public SpduParser(SpduCallback callback) {
		this.callback = callback;
	}

	public void parse(byte[] data) throws IOException {
		BitInputStream bis = new BitInputStream(data);
		SpduHeader header = new SpduHeader();
		header.setFormat(bis.readUnsignedInt(1));
		if (header.getFormat() == FIXED_SPDU) {
			header.setType(bis.readUnsignedInt(1));
			Plcw message = new Plcw();
			message.setHeader(header);
			if (header.getType() == SHORT_PLCW) {
				message.setRetransmit(bis.readBoolean());
				message.setPcid(bis.readUnsignedInt(1));
				bis.skipBits(1);
				message.setExpeditedFrameCounter(bis.readUnsignedInt(3));
				message.setReportValue(bis.readUnsignedInt(8));
			} else {
				bis.skipBits(9);
				message.setRetransmit(bis.readBoolean());
				message.setPcid(bis.readUnsignedInt(1));
				message.setExpeditedFrameCounter(bis.readUnsignedInt(3));
				message.setReportValue(bis.readUnsignedInt(16));
			}
			callback.onPlsw(message);
			return;
		}

		header.setType(bis.readUnsignedInt(3));
		switch (header.getType()) {
		case TYPE1_DIRECTIVE:
			int lengthBytes = bis.readUnsignedInt(4);
			byte[] directive = new byte[2];
			for (int i = 0; i < lengthBytes; i += 2) {
				directive[0] = bis.readByte();
				directive[1] = bis.readByte();
				BitInputStream dirStream = new BitInputStream(directive);
				int directiveType = directive[1] & 0b111;
				switch (directiveType) {
				case SET_TRANSMITTER_PARAMETERS:
					TransmitterParameters message = new TransmitterParameters();
					message.setHeader(header);
					message.setMode(TransmitterMode.valueOfCode(dirStream.readUnsignedInt(3)));
					message.setDataRate(DataRate.valueOfCode(dirStream.readUnsignedInt(4)));
					message.setModulation(Modulation.valueOfCode(dirStream.readUnsignedInt(1)));
					message.setDataEncoding(DataEncoding.valueOfCode(dirStream.readUnsignedInt(2)));
					message.setFrequency(Frequency.valueOfCode(dirStream.readUnsignedInt(3)));
					callback.onTransmitterParameters(message);
					break;

				default:
					break;
				}
			}
			break;

		default:
			break;
		}

	}
	
}
