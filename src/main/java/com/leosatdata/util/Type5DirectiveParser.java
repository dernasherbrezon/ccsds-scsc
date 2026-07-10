package com.leosatdata.util;

import java.io.IOException;

import com.leosatdata.model.Coherency;
import com.leosatdata.model.DaySegmented;
import com.leosatdata.model.Duplex;
import com.leosatdata.model.LinkDirection;
import com.leosatdata.model.ModulationIndex;
import com.leosatdata.model.PnRanging;
import com.leosatdata.model.Polarization;
import com.leosatdata.model.RangingCode;
import com.leosatdata.model.RangingModulationIndex;
import com.leosatdata.model.RangingReportRequest;
import com.leosatdata.model.RangingType;
import com.leosatdata.model.ReportRequest;
import com.leosatdata.model.TransceiverMode;
import com.leosatdata.model.Type4CarrierModulation;
import com.leosatdata.model.Type4Coding;
import com.leosatdata.model.Type5Directive;
import com.leosatdata.model.Type5LinkEstablishment;
import com.leosatdata.model.Type5NegotiationType;
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

	public Type5Directive decode(BitInputStream bis) throws IOException {
		Type5Directive result = new Type5Directive();
		while (bis.available() > 0) {
			int type = bis.readUnsignedInt(3);
			switch (type) {
			case LINK2_ESTABLISHMENT_CONTROL:
				Type5LinkEstablishment link = new Type5LinkEstablishment();
				bis.skipBits(1);
				LinkDirection direction = LinkDirection.valueOfCode(bis.readUnsignedInt(1));
				link.setDemandQuery(Type5NegotiationType.valueOfCode(bis.readUnsignedInt(3)));
				link.setRnmd(bis.readBoolean());
				link.setTransmitToken(bis.readBoolean());
				link.setDuplex(Duplex.valueOfCode(bis.readUnsignedInt(3)));
				link.setPolarization(Polarization.valueOfCode(bis.readUnsignedInt(1)));
				link.setCoherency(Coherency.valueOfCode(bis.readUnsignedInt(1)));
				bis.skipBits(1);
				link.setCarrierModulation(Type4CarrierModulation.valueOfCode(bis.readUnsignedInt(4)));
				link.setModulationIndex(ModulationIndex.valueOfCode(bis.readUnsignedInt(3)));
				bis.skipBits(1);
				link.setCoding(Type4Coding.valueOfCode(bis.readUnsignedInt(5)));
				link.setLinkSnr(bis.readUnsignedInt(8));
				link.setTransceiverMode(TransceiverMode.valueOfCode(bis.readUnsignedInt(3)));
				link.setAosFrameLength(bis.readUnsignedInt(16));
				// IEEE 754 standard for half-precision floating point numbers
				link.setSymbolRate(Float.float16ToFloat((short) bis.readUnsignedInt(16)));
				link.setFrequency(bis.readFloat());
				if (direction.equals(LinkDirection.RETURN)) {
					result.setRxLink(link);
				} else {
					result.setTxLink(link);
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
				PnRanging pnRanging = new PnRanging();
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
			encodeLink(LinkDirection.RETURN, message.getRxLink(), bos);
		}
		if (message.getTxLink() != null) {
			encodeLink(LinkDirection.FORWARD, message.getTxLink(), bos);
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
			PnRanging params = message.getPnRanging();
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

	private static void encodeLink(LinkDirection direction, Type5LinkEstablishment params, BitOutputStream bos) throws IOException {
		bos.writeUnsignedInt(3, LINK2_ESTABLISHMENT_CONTROL);
		bos.writeUnsignedInt(1, 0); // unused
		bos.writeUnsignedInt(1, direction.getCode());
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
		bos.writeUnsignedInt(16, (int) Float.floatToFloat16(params.getSymbolRate()));
		bos.writeFloat(params.getFrequency());
	}

}
