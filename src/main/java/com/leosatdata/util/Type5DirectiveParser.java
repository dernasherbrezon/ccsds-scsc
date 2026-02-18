package com.leosatdata.util;

import java.io.IOException;

import com.leosatdata.model.Coding;
import com.leosatdata.model.DaySegmented;
import com.leosatdata.model.Duplex;
import com.leosatdata.model.Link2Establishment;
import com.leosatdata.model.LinkDirection;
import com.leosatdata.model.LinkModulation;
import com.leosatdata.model.Modulation;
import com.leosatdata.model.ModulationIndex;
import com.leosatdata.model.NegotiationType2;
import com.leosatdata.model.PnRaging;
import com.leosatdata.model.Polarization;
import com.leosatdata.model.RangingCode;
import com.leosatdata.model.RangingModulationIndex;
import com.leosatdata.model.RangingReportRequest;
import com.leosatdata.model.RangingType;
import com.leosatdata.model.ReportRequest;
import com.leosatdata.model.SpduHeader;
import com.leosatdata.model.TransceiverMode;
import com.leosatdata.model.Type5Directive;
import com.leosatdata.model.Vr;

public class Type5DirectiveParser {

	// Second Generation Lunar Use
	private final static int LINK2_ESTABLISHMENT_CONTROL = 0b000;
	private final static int LINK2_REPORT_REQUEST = 0b001;
	private final static int LINK2_SET_VR = 0b010;
	private final static int LINK2_REPORT_SOURCE_SCID = 0b011;
	private final static int LINK2_PN_RANGING = 0b100;

	public static Type5Directive decode(SpduHeader header, BitInputStream bis) throws IOException {
		Type5Directive result = new Type5Directive();
		result.setHeader(header);
		while (bis.available() > 0) {
			int type = bis.readUnsignedInt(3);
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
				result.setLinkEstablishment(link2);
				break;
			case LINK2_REPORT_REQUEST:
				ReportRequest reportRequest = new ReportRequest();
				reportRequest.setPcid0PlcwRequest(bis.readBoolean());
				reportRequest.setPcid1PlcwRequest(bis.readBoolean());
				reportRequest.setTimeTagRequest(bis.readUnsignedInt(6));
				reportRequest.setStatusReportRequest(bis.readUnsignedInt(5));
				result.setReportRequest(reportRequest);
				break;
			case LINK2_SET_VR:
				Vr vr = new Vr();
				bis.skipBits(5);
				vr.setFrameSequenceNumber(bis.readUnsignedInt(8));
				result.setVr(vr);
				break;
			case LINK2_REPORT_SOURCE_SCID:
				bis.skipBits(13);
				result.setSourceSpacecraftId(bis.readUnsignedInt(16));
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
				result.setPnRanging(pnRanging);
				break;
			default:
				break;
			}
		}
		return result;
	}

	public static void encode(Type5Directive message, BitOutputStream payload) {
		// TODO Auto-generated method stub

	}

}
