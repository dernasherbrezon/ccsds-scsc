package com.leosatdata.util;

import java.io.IOException;

import com.leosatdata.model.Type4Coding;
import com.leosatdata.model.Duplex;
import com.leosatdata.model.LinkDirection;
import com.leosatdata.model.Type4LinkEstablishment;
import com.leosatdata.model.Type4CarrierModulation;
import com.leosatdata.model.ModulationIndex;
import com.leosatdata.model.Type4NegotiationType;
import com.leosatdata.model.Polarization;
import com.leosatdata.model.QueryResponse;
import com.leosatdata.model.ReportRequest;
import com.leosatdata.model.SpduHeader;
import com.leosatdata.model.Type4Directive;
import com.leosatdata.model.Vr;

public class Type4DirectiveParser {

	// First Generation Lunar Use
	private final static int LINK_ESTABLISHMENT_CONTROL = 0b000;
	private final static int LINK_REPORT_REQUEST = 0b001;
	private final static int LINK_SET_VR = 0b010;
	private final static int LINK_REPORT_SOURCE_SCID = 0b011;

	private final int maximumFrameSequenceLengthBits;

	public Type4DirectiveParser(Configuration config) {
		this.maximumFrameSequenceLengthBits = config.getInteger("maximum_frame_sequence_length", 1) * 8;
	}

	public Type4Directive decode(SpduHeader header, BitInputStream bis) throws IOException {
		Type4Directive result = new Type4Directive();
		result.setHeader(header);
		while (bis.available() > 0) {
			int type = bis.readUnsignedInt(3);
			switch (type) {
			case LINK_ESTABLISHMENT_CONTROL:
				Type4LinkEstablishment linkEstablishment = new Type4LinkEstablishment();
				linkEstablishment.setDirection(LinkDirection.valueOfCode(bis.readUnsignedInt(1)));
				linkEstablishment.setDemandQuery(Type4NegotiationType.valueOfCode(bis.readUnsignedInt(1)));
				linkEstablishment.setQueryResponse(QueryResponse.valueOfCode(bis.readUnsignedInt(1)));
				linkEstablishment.setRnmd(bis.readBoolean());
				linkEstablishment.setTransmitToken(bis.readBoolean());
				linkEstablishment.setDuplex(Duplex.valueOfCode(bis.readUnsignedInt(3)));
				linkEstablishment.setFreqChannel(bis.readUnsignedInt(5));
				linkEstablishment.setPolarization(Polarization.valueOfCode(bis.readUnsignedInt(1)));
				linkEstablishment.setModulationIndex(ModulationIndex.valueOfCode(bis.readUnsignedInt(3)));
				linkEstablishment.setCarrierModulation(Type4CarrierModulation.valueOfCode(bis.readUnsignedInt(4)));
				bis.skipBits(2);
				linkEstablishment.setCoding(Type4Coding.valueOfCode(bis.readUnsignedInt(6)));
				linkEstablishment.setLinkSnr(bis.readUnsignedInt(8));
				linkEstablishment.setSymbolRate(bis.readUnsignedInt(16)); // FIXME convert to float
				if (linkEstablishment.getDirection().equals(LinkDirection.RETURN)) {
					result.setRxLink(linkEstablishment);
				} else {
					result.setTxLink(linkEstablishment);
				}
				break;
			case LINK_REPORT_REQUEST:
				ReportRequest reportRequest = new ReportRequest();
				reportRequest.setPcid0PlcwRequest(bis.readBoolean());
				reportRequest.setPcid1PlcwRequest(bis.readBoolean());
				reportRequest.setTimeTagRequest(bis.readUnsignedInt(6));
				reportRequest.setStatusReportRequest(bis.readUnsignedInt(5));
				result.setReportRequest(reportRequest);
				break;
			case LINK_SET_VR:
				Vr vr = new Vr();
				bis.skipBits(5);
				vr.setFrameSequenceNumber(bis.readUnsignedInt(maximumFrameSequenceLengthBits));
				result.setVr(vr);
				break;
			case LINK_REPORT_SOURCE_SCID:
				bis.skipBits(13);
				result.setSourceSpacecraftId(bis.readUnsignedInt(16));
				break;
			default:
				break;
			}
		}
		return result;
	}

	public void encode(Type4Directive message, BitOutputStream bos) throws IOException {
		if (message.getRxLink() != null) {
			encodeLink(message.getRxLink(), bos);
		}
		if (message.getTxLink() != null) {
			encodeLink(message.getTxLink(), bos);
		}
		if (message.getReportRequest() != null) {
			ReportRequest params = message.getReportRequest();
			bos.writeUnsignedInt(3, LINK_REPORT_REQUEST);
			bos.writeBoolean(params.isPcid0PlcwRequest());
			bos.writeBoolean(params.isPcid1PlcwRequest());
			bos.writeUnsignedInt(6, params.getTimeTagRequest());
			bos.writeUnsignedInt(5, params.getStatusReportRequest());
		}
		if (message.getVr() != null) {
			bos.writeUnsignedInt(3, LINK_SET_VR);
			bos.writeUnsignedInt(5, 0); // unused
			bos.writeUnsignedInt(maximumFrameSequenceLengthBits, message.getVr().getFrameSequenceNumber());
		}
		if (message.getSourceSpacecraftId() != null) {
			bos.writeUnsignedInt(3, LINK_REPORT_SOURCE_SCID);
			bos.writeUnsignedInt(13, 0); // unused
			bos.writeUnsignedInt(16, message.getSourceSpacecraftId());
		}
	}

	private static void encodeLink(Type4LinkEstablishment params, BitOutputStream bos) throws IOException {
		bos.writeUnsignedInt(3, LINK_ESTABLISHMENT_CONTROL);
		bos.writeUnsignedInt(1, params.getDirection().getCode());
		bos.writeUnsignedInt(1, params.getDemandQuery().getCode());
		bos.writeUnsignedInt(1, params.getQueryResponse().getCode());
		bos.writeBoolean(params.isRnmd());
		bos.writeBoolean(params.isTransmitToken());
		bos.writeUnsignedInt(3, params.getDuplex().getCode());
		bos.writeUnsignedInt(5, params.getFreqChannel());
		bos.writeUnsignedInt(1, params.getPolarization().getCode());
		bos.writeUnsignedInt(3, params.getModulationIndex().getCode());
		bos.writeUnsignedInt(4, params.getCarrierModulation().getCode());
		bos.writeUnsignedInt(2, 0); // unused
		bos.writeUnsignedInt(6, params.getCoding().getCode());
		bos.writeUnsignedInt(8, params.getLinkSnr());
		bos.writeUnsignedInt(16, (int) params.getSymbolRate()); // FIXME convert from float
	}

}
