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
import com.leosatdata.model.ReportRequest;
import com.leosatdata.model.RsCodeType;
import com.leosatdata.model.ScramblerType;
import com.leosatdata.model.SpduHeader;
import com.leosatdata.model.TxRxParameters;
import com.leosatdata.model.Type1Directive;
import com.leosatdata.model.Vr;

public class Type1DirectiveParser {

	// Directives/Reports for UHF operations
	private final static int SET_TRANSMITTER_PARAMETERS = 0b000;
	private final static int SET_CONTROL_PARAMETERS = 0b001;
	private final static int SET_RECEIVER_PARAMETERS = 0b010;
	private final static int SET_VR = 0b011;
	private final static int REPORT_REQUEST = 0b100;
	private final static int SET_PL_EXTENSIONS = 0b110;
	private final static int REPORT_SOURCE_SCID = 0b111;

	public static Type1Directive decode(SpduHeader header, int length, BitInputStream bis) throws IOException {
		Type1Directive result = new Type1Directive();
		result.setHeader(header);
		byte[] directive = new byte[2];
		for (int i = 0; i < length; i += 2) {
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
				result.setTxParams(message);
				break;
			case SET_CONTROL_PARAMETERS:
				ControlParameters controlParams = new ControlParameters();
				controlParams.setHeader(header);
				controlParams.setTimeSampleField(dirStream.readUnsignedInt(6));
				controlParams.setDuplex(Duplex.valueOfCode(dirStream.readUnsignedInt(3)));
				dirStream.skipBits(2);
				controlParams.setRnmd(dirStream.readBoolean());
				controlParams.setTransmitToken(dirStream.readBoolean());
				result.setControlParams(controlParams);
				break;
			case SET_RECEIVER_PARAMETERS:
				TxRxParameters rxParams = new TxRxParameters();
				rxParams.setHeader(header);
				rxParams.setMode(Mode.valueOfCode(dirStream.readUnsignedInt(3)));
				rxParams.setDataRate(DataRate.valueOfCode(dirStream.readUnsignedInt(4)));
				rxParams.setModulation(Modulation.valueOfCode(dirStream.readUnsignedInt(1)));
				rxParams.setDataEncoding(DataEncoding.valueOfCode(dirStream.readUnsignedInt(2)));
				rxParams.setFrequency(Frequency.valueOfCode(dirStream.readUnsignedInt(3)));
				result.setRxParams(rxParams);
				break;
			case SET_VR:
				Vr vr = new Vr();
				vr.setHeader(header);
				vr.setFrameSequenceNumber(dirStream.readUnsignedInt(8));
				result.setVr(vr);
				break;
			case REPORT_REQUEST:
				ReportRequest reportReq = new ReportRequest();
				reportReq.setHeader(header);
				dirStream.skipBits(3);
				reportReq.setStatusReportRequest(dirStream.readUnsignedInt(5));
				reportReq.setTimeTagRequest(dirStream.readUnsignedInt(3));
				reportReq.setPcid0PlcwRequest(dirStream.readBoolean());
				reportReq.setPcid1PlcwRequest(dirStream.readBoolean());
				result.setReportReq(reportReq);
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
				result.setPlExtensions(plExtensions);
				break;
			case REPORT_SOURCE_SCID:
				result.setSourceSpacecraftId(dirStream.readUnsignedInt(10));
				break;
			default:
				break;
			}
		}
		return result;
	}

	public static void encode(Type1Directive message, BitOutputStream payload) {
		// TODO Auto-generated method stub

	}

}
