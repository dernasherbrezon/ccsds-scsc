package com.leosatdata.util;

import java.io.IOException;

import com.leosatdata.model.Type1CarrierModulation;
import com.leosatdata.model.ControlParameters;
import com.leosatdata.model.Type1Coding;
import com.leosatdata.model.LineCoding;
import com.leosatdata.model.DataRate;
import com.leosatdata.model.Direction;
import com.leosatdata.model.Duplex;
import com.leosatdata.model.Frequency;
import com.leosatdata.model.Mode;
import com.leosatdata.model.ModeSelect;
import com.leosatdata.model.Coherency;
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
				result.setTxParams(decodeTxRxParameters(dirStream));
				break;
			case SET_RECEIVER_PARAMETERS:
				result.setRxParams(decodeTxRxParameters(dirStream));
				break;
			case SET_CONTROL_PARAMETERS:
				ControlParameters controlParams = new ControlParameters();
				controlParams.setTimeSampleField(dirStream.readUnsignedInt(6));
				controlParams.setDuplex(Duplex.valueOfCode(dirStream.readUnsignedInt(3)));
				dirStream.skipBits(2);
				controlParams.setRnmd(dirStream.readBoolean());
				controlParams.setTransmitToken(dirStream.readBoolean());
				result.setControlParams(controlParams);
				break;
			case SET_VR:
				Vr vr = new Vr();
				vr.setFrameSequenceNumber(dirStream.readUnsignedInt(8));
				dirStream.skipBits(5);
				result.setVr(vr);
				break;
			case REPORT_REQUEST:
				ReportRequest reportReq = new ReportRequest();
				dirStream.skipBits(3);
				reportReq.setStatusReportRequest(dirStream.readUnsignedInt(5));
				reportReq.setTimeTagRequest(dirStream.readUnsignedInt(3));
				reportReq.setPcid0PlcwRequest(dirStream.readBoolean());
				reportReq.setPcid1PlcwRequest(dirStream.readBoolean());
				result.setReportReq(reportReq);
				break;
			case SET_PL_EXTENSIONS:
				PlExtensions plExtensions = new PlExtensions();
				plExtensions.setDirection(Direction.valueOfCode(dirStream.readUnsignedInt(1)));
				plExtensions.setUseExtendedFreqSet(dirStream.readBoolean());
				plExtensions.setUseExtendedDataRate(dirStream.readBoolean());
				plExtensions.setCarrierModulation(Type1CarrierModulation.valueOfCode(dirStream.readUnsignedInt(2)));
				plExtensions.setLineCoding(LineCoding.valueOfCode(dirStream.readUnsignedInt(2)));
				plExtensions.setModeSelect(ModeSelect.valueOfCode(dirStream.readUnsignedInt(2)));
				plExtensions.setScrambler(ScramblerType.valueOfCode(dirStream.readUnsignedInt(2)));
				plExtensions.setDifferentialEncoding(dirStream.readBoolean());
				plExtensions.setRsCode(RsCodeType.valueOfCode(dirStream.readUnsignedInt(1)));
				if (plExtensions.getDirection().equals(Direction.RECEIVER)) {
					result.setRxPlExtensions(plExtensions);
				} else {
					result.setTxPlExtensions(plExtensions);
				}
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

	public static void encode(Type1Directive message, BitOutputStream bos) throws IOException {
		if (message.getTxParams() != null) {
			encodeTxRxParameters(SET_TRANSMITTER_PARAMETERS, message.getTxParams(), bos);
		}
		if (message.getRxParams() != null) {
			encodeTxRxParameters(SET_RECEIVER_PARAMETERS, message.getRxParams(), bos);
		}
		if (message.getControlParams() != null) {
			ControlParameters params = message.getControlParams();
			bos.writeUnsignedInt(6, params.getTimeSampleField());
			bos.writeUnsignedInt(3, params.getDuplex().getCode());
			bos.writeUnsignedInt(2, 0); // unused
			bos.writeBoolean(params.isRnmd());
			bos.writeBoolean(params.isTransmitToken());
			bos.writeUnsignedInt(3, SET_CONTROL_PARAMETERS);
		}
		if (message.getVr() != null) {
			bos.writeUnsignedInt(8, message.getVr().getFrameSequenceNumber());
			bos.writeUnsignedInt(5, 0);// unused
			bos.writeUnsignedInt(3, SET_VR);
		}
		if (message.getReportReq() != null) {
			ReportRequest params = message.getReportReq();
			bos.writeUnsignedInt(3, 0); // unused
			bos.writeUnsignedInt(5, params.getStatusReportRequest());
			bos.writeUnsignedInt(3, params.getTimeTagRequest());
			bos.writeBoolean(params.isPcid0PlcwRequest());
			bos.writeBoolean(params.isPcid1PlcwRequest());
			bos.writeUnsignedInt(3, REPORT_REQUEST);
		}
		if (message.getRxPlExtensions() != null) {
			message.getRxPlExtensions().setDirection(Direction.RECEIVER);
			encodePlExtensions(message.getRxPlExtensions(), bos);
		}
		if (message.getTxPlExtensions() != null) {
			message.getTxPlExtensions().setDirection(Direction.TRANSMITTER);
			encodePlExtensions(message.getTxPlExtensions(), bos);
		}
		if (message.getSourceSpacecraftId() != null) {
			bos.writeUnsignedInt(10, message.getSourceSpacecraftId());
			bos.writeUnsignedInt(3, 0); // unused
			bos.writeUnsignedInt(3, REPORT_SOURCE_SCID);
		}
	}

	private static TxRxParameters decodeTxRxParameters(BitInputStream dirStream) throws IOException {
		TxRxParameters message = new TxRxParameters();
		message.setMode(Mode.valueOfCode(dirStream.readUnsignedInt(3)));
		message.setDataRate(DataRate.valueOfCode(dirStream.readUnsignedInt(4)));
		message.setCoherency(Coherency.valueOfCode(dirStream.readUnsignedInt(1)));
		message.setCoding(Type1Coding.valueOfCode(dirStream.readUnsignedInt(2)));
		message.setFrequency(Frequency.valueOfCode(dirStream.readUnsignedInt(3)));
		return message;
	}

	private static void encodeTxRxParameters(int type, TxRxParameters params, BitOutputStream bos) throws IOException {
		bos.writeUnsignedInt(3, params.getMode().getCode());
		bos.writeUnsignedInt(4, params.getDataRate().getCode());
		bos.writeUnsignedInt(1, params.getCoherency().getCode());
		bos.writeUnsignedInt(2, params.getCoding().getCode());
		bos.writeUnsignedInt(3, params.getFrequency().getCode());
		bos.writeUnsignedInt(3, type);
	}

	private static void encodePlExtensions(PlExtensions params, BitOutputStream bos) throws IOException {
		bos.writeUnsignedInt(1, params.getDirection().getCode());
		bos.writeBoolean(params.isUseExtendedFreqSet());
		bos.writeBoolean(params.isUseExtendedDataRate());
		bos.writeUnsignedInt(2, params.getCarrierModulation().getCode());
		bos.writeUnsignedInt(2, params.getLineCoding().getCode());
		bos.writeUnsignedInt(2, params.getModeSelect().getCode());
		bos.writeUnsignedInt(2, params.getScrambler().getCode());
		bos.writeBoolean(params.isDifferentialEncoding());
		bos.writeUnsignedInt(1, params.getRsCode().getCode());
		bos.writeUnsignedInt(3, SET_PL_EXTENSIONS);
	}

}
