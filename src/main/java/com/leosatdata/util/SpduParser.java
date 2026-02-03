package com.leosatdata.util;

import java.io.IOException;

import com.leosatdata.model.CarrierModulation;
import com.leosatdata.model.Coding;
import com.leosatdata.model.ControlParameters;
import com.leosatdata.model.DataEncoding;
import com.leosatdata.model.DataModulation;
import com.leosatdata.model.DataRate;
import com.leosatdata.model.DaySegmented;
import com.leosatdata.model.Direction;
import com.leosatdata.model.Duplex;
import com.leosatdata.model.Frequency;
import com.leosatdata.model.Link2Establishment;
import com.leosatdata.model.LinkDirection;
import com.leosatdata.model.LinkEstablishment;
import com.leosatdata.model.LinkModulation;
import com.leosatdata.model.Mode;
import com.leosatdata.model.ModeSelect;
import com.leosatdata.model.Modulation;
import com.leosatdata.model.ModulationIndex;
import com.leosatdata.model.NegotiationType;
import com.leosatdata.model.NegotiationType2;
import com.leosatdata.model.PlExtensions;
import com.leosatdata.model.Plcw;
import com.leosatdata.model.PnRaging;
import com.leosatdata.model.Polarization;
import com.leosatdata.model.QueryResponse;
import com.leosatdata.model.RangingCode;
import com.leosatdata.model.RangingModulationIndex;
import com.leosatdata.model.RangingReportRequest;
import com.leosatdata.model.RangingType;
import com.leosatdata.model.ReportRequest;
import com.leosatdata.model.RsCodeType;
import com.leosatdata.model.ScramblerType;
import com.leosatdata.model.SpduHeader;
import com.leosatdata.model.TimeDistribution;
import com.leosatdata.model.TimeDistributionType;
import com.leosatdata.model.TransceiverMode;
import com.leosatdata.model.TxRxParameters;
import com.leosatdata.model.Vr;

public class SpduParser {

	private final static int FIXED_SPDU = 1;
	private final static int VARIABLE_SPDU = 0;

	private final static int SHORT_PLCW = 0; // Type F1
	private final static int LONG_PLCW = 1; // Type F2

	private final static int TYPE1_DIRECTIVE = 0b000;
	private final static int TYPE2_DIRECTIVE = 0b001;
	private final static int TYPE3_DIRECTIVE = 0b010;
	private final static int TYPE4_DIRECTIVE = 0b011;
	private final static int TYPE5_DIRECTIVE = 0b100;

  // Directives/Reports for UHF operations 
	private final static int SET_TRANSMITTER_PARAMETERS = 0b000;
	private final static int SET_CONTROL_PARAMETERS = 0b001;
	private final static int SET_RECEIVER_PARAMETERS = 0b010;
	private final static int SET_VR = 0b011;
	private final static int REPORT_REQUEST = 0b100;
	private final static int SET_PL_EXTENSIONS = 0b110;
	private final static int REPORT_SOURCE_SCID = 0b111;

  // First Generation Lunar Use
	private final static int LINK_ESTABLISHMENT_CONTROL = 0b000;
	private final static int LINK_REPORT_REQUEST = 0b001;
	private final static int LINK_SET_VR = 0b010;
	private final static int LINK_REPORT_SOURCE_SCID = 0b011;

  // Second Generation Lunar Use 
	private final static int LINK2_ESTABLISHMENT_CONTROL = 0b000;
	private final static int LINK2_REPORT_REQUEST = 0b001;
	private final static int LINK2_SET_VR = 0b010;
	private final static int LINK2_REPORT_SOURCE_SCID = 0b011;
	private final static int LINK2_PN_RANGING = 0b100;

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
		int lengthBytes = bis.readUnsignedInt(4);
		switch (header.getType()) {
		case TYPE1_DIRECTIVE:
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
					callback.onSourceSpacecraftId(dirStream.readUnsignedInt(10)); // SCID
					break;
				default:
					break;
				}
			}
			break;
		case TYPE2_DIRECTIVE:
			TimeDistribution timeDistribution = new TimeDistribution();
			timeDistribution.setType(TimeDistributionType.valueOfCode(bis.readUnsignedByte()));
			timeDistribution.setTransceiverClock(bis.readLong());
			timeDistribution.setSendSideDelay(bis.readUnsignedInt(3 * 8));
			timeDistribution.setOneWayLightTime(bis.readUnsignedInt(3 * 8));
			callback.onTimeDistribution(timeDistribution);
			break;
		case TYPE3_DIRECTIVE:
			byte[] statusReport = new byte[16];
			bis.readFully(statusReport);
			callback.onStatusReport(statusReport);
			break;
		case TYPE4_DIRECTIVE:
			int type = bis.readUnsignedInt(3);
			switch (type) {
			case LINK_ESTABLISHMENT_CONTROL:
				LinkEstablishment linkEstablishment = new LinkEstablishment();
				linkEstablishment.setHeader(header);
				linkEstablishment.setDirection(LinkDirection.valueOfCode(bis.readUnsignedInt(1)));
				linkEstablishment.setDemandQuery(NegotiationType.valueOfCode(bis.readUnsignedInt(1)));
				linkEstablishment.setQueryResponse(QueryResponse.valueOfCode(bis.readUnsignedInt(1)));
				linkEstablishment.setRnmd(bis.readBoolean());
				linkEstablishment.setTransmitToken(bis.readBoolean());
				linkEstablishment.setDuplex(Duplex.valueOfCode(bis.readUnsignedInt(3)));
				linkEstablishment.setFreqChannel(bis.readUnsignedInt(5));
				linkEstablishment.setPolarization(Polarization.valueOfCode(bis.readUnsignedInt(1)));
				linkEstablishment.setModulationIndex(ModulationIndex.valueOfCode(bis.readUnsignedInt(3)));
				linkEstablishment.setModulation(LinkModulation.valueOfCode(bis.readUnsignedInt(4)));
				bis.skipBits(2);
				linkEstablishment.setCoding(Coding.valueOfCode(bis.readUnsignedInt(6)));
				linkEstablishment.setLinkSnr(bis.readUnsignedInt(8));
				linkEstablishment.setSymbolRate(bis.readUnsignedInt(16)); // FIXME convert to float
				callback.onLinkEstablishment(linkEstablishment);
				break;
			case LINK_REPORT_REQUEST:
				ReportRequest reportRequest = new ReportRequest();
				reportRequest.setHeader(header);
				reportRequest.setPcid0PlcwRequest(bis.readBoolean());
				reportRequest.setPcid1PlcwRequest(bis.readBoolean());
				reportRequest.setTimeTagRequest(bis.readUnsignedInt(6));
				reportRequest.setStatusReportRequest(bis.readUnsignedInt(5));
				callback.onReportRequest(reportRequest);
				break;
			case LINK_SET_VR:
				Vr vr = new Vr();
				bis.skipBits(5);
				vr.setHeader(header);
				vr.setFrameSequenceNumber(bis.readUnsignedInt(8));
				callback.onVr(vr);
				break;
			case LINK_REPORT_SOURCE_SCID:
				bis.skipBits(13);
				callback.onSourceSpacecraftId(bis.readUnsignedInt(16));
				break;
			default:
				break;
			}
			break;
		case TYPE5_DIRECTIVE:
			type = bis.readUnsignedInt(3);
			switch (type) {
			case LINK2_ESTABLISHMENT_CONTROL:
				Link2Establishment link2 = new Link2Establishment();
				link2.setHeader(header);
				bis.skipBits(1);
				link2.setDirection(LinkDirection.valueOfCode(bis.readUnsignedInt(1)));
				link2.setDemandQuery(NegotiationType2.valueOfCode(bis.readUnsignedInt(3)));
				link2.setRnmd(bis.readBoolean());
				link2.setTransmitToken(bis.readBoolean());
				link2.setDuplex(Duplex.valueOfCode(bis.readUnsignedInt(3)));
				link2.setPolarization(Polarization.valueOfCode(bis.readUnsignedInt(1)));
				link2.setCoherency(Modulation.valueOfCode(bis.readUnsignedInt(1)));
				bis.skipBits(1);
				link2.setModulation(LinkModulation.valueOfCode(bis.readUnsignedInt(4)));
				link2.setModulationIndex(ModulationIndex.valueOfCode(bis.readUnsignedInt(3)));
				bis.skipBits(1);
				link2.setCoding(Coding.valueOfCode(bis.readUnsignedInt(5)));
				link2.setLinkSnr(bis.readUnsignedInt(8));
				link2.setTransceiverMode(TransceiverMode.valueOfCode(bis.readUnsignedInt(3)));
				link2.setAosFrameLength(bis.readUnsignedInt(16));
				link2.setSymbolRate(bis.readUnsignedInt(16));
				link2.setFrequency(bis.readFloat());
				callback.onLink2Establishment(link2);
				break;
			case LINK2_REPORT_REQUEST:
				ReportRequest reportRequest = new ReportRequest();
				reportRequest.setHeader(header);
				reportRequest.setPcid0PlcwRequest(bis.readBoolean());
				reportRequest.setPcid1PlcwRequest(bis.readBoolean());
				reportRequest.setTimeTagRequest(bis.readUnsignedInt(6));
				reportRequest.setStatusReportRequest(bis.readUnsignedInt(5));
				callback.onReportRequest(reportRequest);
				break;
			case LINK2_SET_VR:
				Vr vr = new Vr();
				vr.setHeader(header);
				bis.skipBits(5);
				vr.setFrameSequenceNumber(bis.readUnsignedInt(8));
				callback.onVr(vr);
				break;
			case LINK2_REPORT_SOURCE_SCID:
				bis.skipBits(13);
				callback.onSourceSpacecraftId(bis.readUnsignedInt(16));
				break;
			case LINK2_PN_RANGING:
				PnRaging pnRanging = new PnRaging();
				pnRanging.setRangingType(RangingType.valueOfCode(bis.readUnsignedInt(2)));
				pnRanging.setRangingCode(RangingCode.valueOfCode(bis.readUnsignedInt(2)));
				pnRanging.setChipRateK(bis.readUnsignedInt(3));
				pnRanging.setChipRateL(bis.readUnsignedInt(14));
				pnRanging.setChipRateM(bis.readUnsignedInt(14));
				pnRanging.setModulationIndex(RangingModulationIndex.valueOfCode(bis.readUnsignedInt(3)));
				DaySegmented daySegmented = new DaySegmented();
				daySegmented.setDay(bis.readUnsignedInt(bis.readUnsignedInt(16)));
				daySegmented.setMillisecondsOfTheDay(bis.readUnsignedInt(32));
				pnRanging.setPnEpochTime(daySegmented);
				pnRanging.setReportRequest(RangingReportRequest.valueOfCode(bis.readUnsignedInt(5)));
				callback.onPnRanging(pnRanging);
				break;
			default:
				break;
			}
			break;
		default:
			break;
		}

	}

}
