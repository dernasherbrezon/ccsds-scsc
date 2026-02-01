package com.leosatdata.util;

import java.io.IOException;

import com.leosatdata.model.CarrierModulation;
import com.leosatdata.model.ControlParameters;
import com.leosatdata.model.DataEncoding;
import com.leosatdata.model.DataModulation;
import com.leosatdata.model.DataRate;
import com.leosatdata.model.Direction;
import com.leosatdata.model.Duplex;
import com.leosatdata.model.Frequency;
import com.leosatdata.model.Mode;
import com.leosatdata.model.ModeSelect;
import com.leosatdata.model.Modulation;
import com.leosatdata.model.PlExtensions;
import com.leosatdata.model.Plcw;
import com.leosatdata.model.ReportRequest;
import com.leosatdata.model.RsCodeType;
import com.leosatdata.model.ScramblerType;
import com.leosatdata.model.SpduHeader;
import com.leosatdata.model.TxRxParameters;
import com.leosatdata.model.Vr;

public class SpduParser {

	private final static int FIXED_SPDU = 1;
	private final static int VARIABLE_SPDU = 0;

	private final static int SHORT_PLCW = 0; // Type F1
	private final static int LONG_PLCW = 1; // Type F2

	private final static int TYPE1_DIRECTIVE = 0b000;

	private final static int SET_TRANSMITTER_PARAMETERS = 0b000;
	private final static int SET_CONTROL_PARAMETERS = 0b001;
	private final static int SET_RECEIVER_PARAMETERS = 0b010;
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
			} else { // LONG_PLCW
				bis.skipBits(9);
				message.setRetransmit(bis.readBoolean());
				message.setPcid(bis.readUnsignedInt(1));
				message.setExpeditedFrameCounter(bis.readUnsignedInt(3));
				message.setReportValue(bis.readUnsignedInt(16));
			}
			callback.onPlsw(message);
			return;
		}

		// VARIABLE_SPDU
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
					TxRxParameters message = new TxRxParameters();
					message.setHeader(header);
					message.setMode(Mode.valueOfCode(dirStream.readUnsignedInt(3)));
					message.setDataRate(DataRate.valueOfCode(dirStream.readUnsignedInt(4)));
					message.setModulation(Modulation.valueOfCode(dirStream.readUnsignedInt(1)));
					message.setDataEncoding(DataEncoding.valueOfCode(dirStream.readUnsignedInt(2)));
					message.setFrequency(Frequency.valueOfCode(dirStream.readUnsignedInt(3)));
					callback.onTransmitterParameters(message);
					break;
				case SET_CONTROL_PARAMETERS:
					ControlParameters controlParams = new ControlParameters();
					controlParams.setHeader(header);
					controlParams.setTimeSampleField(dirStream.readUnsignedInt(6));
					controlParams.setDuplex(Duplex.valueOfCode(dirStream.readUnsignedInt(3)));
					dirStream.skipBits(2);
					controlParams.setRnmd(dirStream.readBoolean());
					controlParams.setTransmitToken(dirStream.readBoolean());
					callback.onControlParameters(controlParams);
					break;
				case SET_RECEIVER_PARAMETERS:
					TxRxParameters rxParams = new TxRxParameters();
					rxParams.setHeader(header);
					rxParams.setMode(Mode.valueOfCode(dirStream.readUnsignedInt(3)));
					rxParams.setDataRate(DataRate.valueOfCode(dirStream.readUnsignedInt(4)));
					rxParams.setModulation(Modulation.valueOfCode(dirStream.readUnsignedInt(1)));
					rxParams.setDataEncoding(DataEncoding.valueOfCode(dirStream.readUnsignedInt(2)));
					rxParams.setFrequency(Frequency.valueOfCode(dirStream.readUnsignedInt(3)));
					callback.onReceiverParameters(rxParams);
					break;
				case SET_VR:
					Vr vr = new Vr();
					vr.setHeader(header);
					vr.setFrameSequenceNumber(dirStream.readUnsignedInt(8));
					callback.onVr(vr);
					break;
				case REPORT_REQUEST:
					ReportRequest reportReq = new ReportRequest();
					reportReq.setHeader(header);
					dirStream.skipBits(3);
					reportReq.setStatusReportRequest(dirStream.readUnsignedInt(5));
					reportReq.setTimeTagRequest(dirStream.readUnsignedInt(3));
					reportReq.setPcid0PlcwRequest(dirStream.readBoolean());
					reportReq.setPcid1PlcwRequest(dirStream.readBoolean());
					callback.onReportRequest(reportReq);
					break;
				case SET_PL_EXTENSIONS:
					PlExtensions plExtensions = new PlExtensions();
					plExtensions.setHeader(header);
					plExtensions.setDirection(Direction.valueOfCode(dirStream.readUnsignedInt(1)));
					plExtensions.setUseExtendedFreqSet(dirStream.readBoolean());
					plExtensions.setUseExtendedDataRate(dirStream.readBoolean());
					plExtensions.setCarrierModulation(CarrierModulation.valueOfCode(dirStream.readUnsignedInt(2)));
					plExtensions.setDataModulation(DataModulation.valueOfCode(dirStream.readUnsignedInt(2)));
					plExtensions.setModeSelect(ModeSelect.valueOfCode(dirStream.readUnsignedInt(2)));
					plExtensions.setScrambler(ScramblerType.valueOfCode(dirStream.readUnsignedInt(2)));
					plExtensions.setDifferentialEncoding(dirStream.readBoolean());
					plExtensions.setRsCode(RsCodeType.valueOfCode(dirStream.readUnsignedInt(1)));
					callback.onPlExtensions(plExtensions);
					break;
				case REPORT_SOURCE_SCID:
					int sourceSpacecraftId = dirStream.readUnsignedInt(10); // SCID
					callback.onSourceSpacecraftId(sourceSpacecraftId);
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
