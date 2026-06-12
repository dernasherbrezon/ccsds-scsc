package com.leosatdata.util;

import java.io.IOException;

import com.leosatdata.model.Type4Coding;
import com.leosatdata.model.DaySegmented;
import com.leosatdata.model.Duplex;
import com.leosatdata.model.Link2Establishment;
import com.leosatdata.model.LinkDirection;
import com.leosatdata.model.Type4CarrierModulation;
import com.leosatdata.model.Coherency;
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

	private final int maximumFrameSequenceLengthBits;

	public Type5DirectiveParser(Configuration config) {
		this.maximumFrameSequenceLengthBits = config.getInteger("maximum_frame_sequence_length", 1) * 8;
	}
	
	public Type5Directive decode(SpduHeader header, BitInputStream bis) throws IOException {
		Type5Directive result = new Type5Directive();
		result.setHeader(header);
		while (bis.available() > 0) {
			int type = bis.readUnsignedInt(3);
			switch (type) {
			case LINK2_ESTABLISHMENT_CONTROL:
				Link2Establishment link2 = new Link2Establishment();
				bis.skipBits(1);
				link2.setDirection(LinkDirection.valueOfCode(bis.readUnsignedInt(1)));
				link2.setDemandQuery(NegotiationType2.valueOfCode(bis.readUnsignedInt(3)));
				link2.setRnmd(bis.readBoolean());
				link2.setTransmitToken(bis.readBoolean());
				link2.setDuplex(Duplex.valueOfCode(bis.readUnsignedInt(3)));
				link2.setPolarization(Polarization.valueOfCode(bis.readUnsignedInt(1)));
				link2.setCoherency(Coherency.valueOfCode(bis.readUnsignedInt(1)));
				bis.skipBits(1);
				link2.setCarrierModulation(Type4CarrierModulation.valueOfCode(bis.readUnsignedInt(4)));
				link2.setModulationIndex(ModulationIndex.valueOfCode(bis.readUnsignedInt(3)));
				bis.skipBits(1);
				link2.setCoding(Type4Coding.valueOfCode(bis.readUnsignedInt(5)));
				link2.setLinkSnr(bis.readUnsignedInt(8));
				link2.setTransceiverMode(TransceiverMode.valueOfCode(bis.readUnsignedInt(3)));
				link2.setAosFrameLength(bis.readUnsignedInt(16));
				link2.setSymbolRate(bis.readUnsignedInt(16)); // FIXME convert to float
				link2.setFrequency(bis.readFloat());
				if (link2.getDirection().equals(LinkDirection.RETURN)) {
					result.setRxLink(link2);
				} else {
					result.setTxLink(link2);
				}
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
				vr.setFrameSequenceNumber(bis.readUnsignedInt(maximumFrameSequenceLengthBits));
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
				daySegmented.setMillisecondsOfTheDay(bis.readUnsignedLong(32));
				pnRanging.setPnEpochTime(daySegmented);
				pnRanging.setReportRequest(RangingReportRequest.valueOfCode(bis.readUnsignedInt(5)));
				bis.skipBits(2);
				result.setPnRanging(pnRanging);
				break;
			default:
				break;
			}
		}
		return result;
	}

	public void encode(Type5Directive message, BitOutputStream bos) throws IOException {
		if (message.getRxLink() != null) {
			encodeLink(message.getRxLink(), bos);
		}
		if (message.getTxLink() != null) {
			encodeLink(message.getTxLink(), bos);
		}
		if (message.getReportRequest() != null) {
			ReportRequest params = message.getReportRequest();
			bos.writeUnsignedInt(3, LINK2_REPORT_REQUEST);
			bos.writeBoolean(params.isPcid0PlcwRequest());
			bos.writeBoolean(params.isPcid1PlcwRequest());
			bos.writeUnsignedInt(6, params.getTimeTagRequest());
			bos.writeUnsignedInt(5, params.getStatusReportRequest());
		}
		if (message.getVr() != null) {
			bos.writeUnsignedInt(3, LINK2_SET_VR);
			bos.writeUnsignedInt(5, 0); // unused
			bos.writeUnsignedInt(maximumFrameSequenceLengthBits, message.getVr().getFrameSequenceNumber());
		}
		if (message.getSourceSpacecraftId() != null) {
			bos.writeUnsignedInt(3, LINK2_REPORT_SOURCE_SCID);
			bos.writeUnsignedInt(13, 0); // unused
			bos.writeUnsignedInt(16, message.getSourceSpacecraftId());
		}
		if (message.getPnRanging() != null) {
			PnRaging params = message.getPnRanging();
			bos.writeUnsignedInt(3, LINK2_PN_RANGING);
			bos.writeUnsignedInt(2, params.getRangingType().getCode());
			bos.writeUnsignedInt(2, params.getRangingCode().getCode());
			bos.writeUnsignedInt(3, params.getChipRateK());
			bos.writeUnsignedInt(14, params.getChipRateL());
			bos.writeUnsignedInt(14, params.getChipRateM());
			bos.writeUnsignedInt(3, params.getModulationIndex().getCode());
			bos.writeUnsignedInt(16, params.getPnEpochTime().getDay());
			bos.writeUnsignedLong(32, params.getPnEpochTime().getMillisecondsOfTheDay());
			bos.writeUnsignedInt(5, params.getReportRequest().getCode());
			bos.writeUnsignedInt(2, 0); // unused
		}

	}

	private static void encodeLink(Link2Establishment params, BitOutputStream bos) throws IOException {
		bos.writeUnsignedInt(3, LINK2_ESTABLISHMENT_CONTROL);
		bos.writeUnsignedInt(1, 0); // unused
		bos.writeUnsignedInt(1, params.getDirection().getCode());
		bos.writeUnsignedInt(3, params.getDemandQuery().getCode());
		bos.writeBoolean(params.isRnmd());
		bos.writeBoolean(params.isTransmitToken());
		bos.writeUnsignedInt(3, params.getDuplex().getCode());
		bos.writeUnsignedInt(1, params.getPolarization().getCode());
		bos.writeUnsignedInt(1, params.getCoherency().getCode());
		bos.writeUnsignedInt(1, 0); // unused
		bos.writeUnsignedInt(4, params.getCarrierModulation().getCode());
		bos.writeUnsignedInt(3, params.getModulationIndex().getCode());
		bos.writeUnsignedInt(1, 0); // unused
		bos.writeUnsignedInt(5, params.getCoding().getCode());
		bos.writeUnsignedInt(8, params.getLinkSnr());
		bos.writeUnsignedInt(3, params.getTransceiverMode().getCode());
		bos.writeUnsignedInt(16, params.getAosFrameLength());
		bos.writeUnsignedInt(16, (int) params.getSymbolRate()); // FIXME convert from float
		bos.writeFloat(params.getFrequency());
	}

}
