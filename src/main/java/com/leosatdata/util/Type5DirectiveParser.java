/*
 * Copyright 2026 Andrey Rodionov
 * SPDX-License-Identifier: Apache-2.0
 */
package com.leosatdata.util;

import java.io.IOException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.leosatdata.model.AlertMessage;
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
import com.leosatdata.model.ReportSourceScid;
import com.leosatdata.model.ServiceRequest;
import com.leosatdata.model.SetFixedLengthFrame;
import com.leosatdata.model.StatusType;
import com.leosatdata.model.SurfaceGroupElevationAngle;
import com.leosatdata.model.TransceiverMode;
import com.leosatdata.model.TurnaroundRatio;
import com.leosatdata.model.Type4CarrierModulation;
import com.leosatdata.model.Type4Coding;
import com.leosatdata.model.Type5Directive;
import com.leosatdata.model.Type5LinkEstablishment;
import com.leosatdata.model.Type5NegotiationType;
import com.leosatdata.model.Type5ReportRequest;
import com.leosatdata.model.Vr;

public class Type5DirectiveParser {

	private static final Logger LOG = LoggerFactory.getLogger(Type5DirectiveParser.class);

	// Second Generation Lunar Use
	private final static int LINK2_ESTABLISHMENT_CONTROL = 0b0000;
	private final static int LINK2_REPORT_REQUEST = 0b0001;
	private final static int LINK2_SET_VR = 0b0010;
	private final static int LINK2_REPORT_SOURCE_SCID = 0b0011;
	private final static int LINK2_SERVICE_REQUEST = 0b0100;
	private final static int LINK2_SET_FIXED_LENGTH_FRAME = 0b0101;
	private final static int LINK2_PN_RANGING = 0b0110;

	public Type5Directive decode(BitInputStream bis) throws IOException {
		Type5Directive result = new Type5Directive();
		while (bis.available() > 0) {
			int type = bis.readUnsignedInt(4);
			switch (type) {
			case LINK2_ESTABLISHMENT_CONTROL:
				Type5LinkEstablishment link = new Type5LinkEstablishment();
				LinkDirection direction = LinkDirection.valueOfCode(bis.readUnsignedInt(1));
				link.setDemandQuery(Type5NegotiationType.valueOfCode(bis.readUnsignedInt(3)));
				link.setRnmd(bis.readBoolean());
				link.setTransmitToken(bis.readBoolean());
				link.setDuplex(Duplex.valueOfCode(bis.readUnsignedInt(3)));
				link.setPolarization(Polarization.valueOfCode(bis.readUnsignedInt(1)));
				link.setCoherency(Coherency.valueOfCode(bis.readUnsignedInt(1)));
				boolean modcod = bis.readBoolean();
				if (modcod) {
					LOG.error("modcod table is not supported");
				}
				link.setCarrierModulation(Type4CarrierModulation.valueOfCode(bis.readUnsignedInt(4)));
				link.setCoding(Type4Coding.valueOfCode(bis.readUnsignedInt(5)));
				bis.skipBits(1);
				link.setModulationIndex(ModulationIndex.valueOfCode(bis.readUnsignedInt(3)));
				bis.skipBits(1);
				link.setTransceiverMode(TransceiverMode.valueOfCode(bis.readUnsignedInt(2)));
				link.setLinkSnr(bis.readUnsignedInt(8));
				link.setTimeSampleField(bis.readUnsignedInt(6));
				bis.skipBits(2);
				// IEEE 754 standard for half-precision floating point numbers
				link.setSymbolRate(Float.float16ToFloat((short) bis.readUnsignedInt(16)) * (1 << 16));
				if (link.getCoherency().equals(Coherency.COHERENT)) {
					TurnaroundRatio ratio = new TurnaroundRatio();
					ratio.setNumerator(bis.readUnsignedInt(16));
					ratio.setDenominator(bis.readUnsignedInt(16));
					link.setTurnaroundRatio(ratio);
				} else {
					link.setFrequency(bis.readFloat());
				}
				if (direction.equals(LinkDirection.RETURN)) {
					result.setRxLink(link);
				} else {
					result.setTxLink(link);
				}
				break;
			case LINK2_REPORT_REQUEST:
				Type5ReportRequest reportRequest = new Type5ReportRequest();
				reportRequest.setPcid0PlcwRequest(bis.readBoolean());
				reportRequest.setPcid1PlcwRequest(bis.readBoolean());
				reportRequest.setLecRequested(bis.readBoolean());
				reportRequest.setStatusReportRequest(bis.readUnsignedInt(3));
				reportRequest.setVcid(bis.readUnsignedInt(6));
				result.setReportRequest(reportRequest);
				break;
			case LINK2_SET_VR:
				Vr vr = new Vr();
				bis.skipBits(4);
				vr.setFrameSequenceNumber(bis.readUnsignedInt(16));
				result.setVr(vr);
				break;
			case LINK2_REPORT_SOURCE_SCID:
				ReportSourceScid report = new ReportSourceScid();
				report.setType(StatusType.valueOfCode(bis.readUnsignedInt(4)));
				if (report.getType().equals(StatusType.MINIMUM_SURFACE_ELEVATION_ANGLE)) {
					SurfaceGroupElevationAngle angle = new SurfaceGroupElevationAngle();
					angle.setDeparting(bis.readBoolean());
					angle.setMinimumElevationAngle(bis.readUnsignedInt(7));
					bis.skipBits(8);
					report.setElevationAngle(angle);
				} else if (report.getType().equals(StatusType.ALERT_MESSAGE)) {
					AlertMessage alert = new AlertMessage();
					alert.setType(bis.readUnsignedInt(3));
					alert.setData(bis.readUnsignedInt(13));
					report.setAlert(alert);
				} else if (report.getType().equals(StatusType.LOCAL_SCID)) {
					report.setSourceSpacecraftId(bis.readUnsignedInt(16));
				} else {
					// unknown
					bis.skipBits(16);
				}
				result.setReportSourceScid(report);
				break;
			case LINK2_SERVICE_REQUEST:
				ServiceRequest serviceRequest = new ServiceRequest();
				serviceRequest.setMissionSpecificEvent(bis.readUnsignedInt(6));
				bis.skipBits(5);
				serviceRequest.setEmergency(bis.readBoolean());
				result.setServiceRequest(serviceRequest);
				break;
			case LINK2_SET_FIXED_LENGTH_FRAME:
				SetFixedLengthFrame fixedLength = new SetFixedLengthFrame();
				fixedLength.setSlicing(bis.readBoolean());
				bis.skipBits(2);
				fixedLength.setDirection(LinkDirection.valueOfCode(bis.readUnsignedInt(1)));
				fixedLength.setFrameLength(bis.readUnsignedInt(16));
				result.setSetFixedLength(fixedLength);
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
				daySegmented.setDay(bis.readUnsignedInt(16));
				daySegmented.setMillisecondsOfTheDay(bis.readUnsignedLong(32));
				pnRanging.setPnEpochTime(daySegmented);
				pnRanging.setReportRequest(RangingReportRequest.valueOfCode(bis.readUnsignedInt(5)));
				bis.skipBits(1);
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
			Type5ReportRequest params = message.getReportRequest();
			bos.writeUnsignedInt(4, LINK2_REPORT_REQUEST);
			bos.writeBoolean(params.isPcid0PlcwRequest());
			bos.writeBoolean(params.isPcid1PlcwRequest());
			bos.writeBoolean(params.isLecRequested());
			bos.writeUnsignedInt(3, params.getStatusReportRequest());
			bos.writeUnsignedInt(6, params.getVcid());
		}
		if (message.getVr() != null) {
			bos.writeUnsignedInt(4, LINK2_SET_VR);
			bos.writeUnsignedInt(4, 0); // unused
			bos.writeUnsignedInt(16, message.getVr().getFrameSequenceNumber());
		}
		ReportSourceScid report = message.getReportSourceScid();
		if (report != null) {
			bos.writeUnsignedInt(4, LINK2_REPORT_SOURCE_SCID);
			bos.writeUnsignedInt(4, report.getType().getCode());
			if (report.getElevationAngle() != null) {
				bos.writeBoolean(report.getElevationAngle().isDeparting());
				bos.writeUnsignedInt(7, report.getElevationAngle().getMinimumElevationAngle());
				bos.writeUnsignedInt(8, 0); // unused
			} else if (report.getAlert() != null) {
				bos.writeUnsignedInt(3, report.getAlert().getType());
				bos.writeUnsignedInt(13, report.getAlert().getData());
			} else if (report.getSourceSpacecraftId() != null) {
				bos.writeUnsignedInt(16, report.getSourceSpacecraftId());
			} else {
				// unknown
				bos.writeUnsignedInt(16, 0);
			}
		}
		if (message.getServiceRequest() != null) {
			bos.writeUnsignedInt(4, LINK2_SERVICE_REQUEST);
			bos.writeUnsignedInt(6, message.getServiceRequest().getMissionSpecificEvent());
			bos.writeUnsignedInt(5, 0); // unused
			bos.writeBoolean(message.getServiceRequest().isEmergency());
		}
		if (message.getSetFixedLength() != null) {
			bos.writeUnsignedInt(4, LINK2_SET_FIXED_LENGTH_FRAME);
			bos.writeBoolean(message.getSetFixedLength().isSlicing());
			bos.writeUnsignedInt(2, 0); // unused
			bos.writeUnsignedInt(1, message.getSetFixedLength().getDirection().getCode());
			bos.writeUnsignedInt(16, message.getSetFixedLength().getFrameLength());
		}
		if (message.getPnRanging() != null) {
			PnRanging params = message.getPnRanging();
			bos.writeUnsignedInt(4, LINK2_PN_RANGING);
			bos.writeUnsignedInt(2, params.getRangingType().getCode());
			bos.writeUnsignedInt(2, params.getRangingCode().getCode());
			bos.writeUnsignedInt(3, params.getChipRateK());
			bos.writeUnsignedInt(14, params.getChipRateL());
			bos.writeUnsignedInt(14, params.getChipRateM());
			bos.writeUnsignedInt(3, params.getModulationIndex().getCode());
			bos.writeUnsignedInt(16, params.getPnEpochTime().getDay());
			bos.writeUnsignedLong(32, params.getPnEpochTime().getMillisecondsOfTheDay());
			bos.writeUnsignedInt(5, params.getReportRequest().getCode());
			bos.writeUnsignedInt(1, 0); // unused
		}

	}

	private static void encodeLink(LinkDirection direction, Type5LinkEstablishment params, BitOutputStream bos) throws IOException {
		bos.writeUnsignedInt(4, LINK2_ESTABLISHMENT_CONTROL);
		bos.writeUnsignedInt(1, direction.getCode());
		bos.writeUnsignedInt(3, params.getDemandQuery().getCode());
		bos.writeBoolean(params.isRnmd());
		bos.writeBoolean(params.isTransmitToken());
		bos.writeUnsignedInt(3, params.getDuplex().getCode());
		bos.writeUnsignedInt(1, params.getPolarization().getCode());
		bos.writeUnsignedInt(1, params.getCoherency().getCode());
		bos.writeBoolean(false); // modcod
		bos.writeUnsignedInt(4, params.getCarrierModulation().getCode());
		bos.writeUnsignedInt(5, params.getCoding().getCode());
		bos.writeUnsignedInt(1, 0); // unused
		bos.writeUnsignedInt(3, params.getModulationIndex().getCode());
		bos.writeUnsignedInt(1, 0); // unused
		bos.writeUnsignedInt(2, params.getTransceiverMode().getCode());
		bos.writeUnsignedInt(8, params.getLinkSnr());
		bos.writeUnsignedInt(6, params.getTimeSampleField());
		bos.writeUnsignedInt(2, 0); // unused
		bos.writeUnsignedInt(16, (int) (Float.floatToFloat16(params.getSymbolRate() / (1 << 16))));
		if (params.getTurnaroundRatio() != null) {
			bos.writeUnsignedInt(16, params.getTurnaroundRatio().getNumerator());
			bos.writeUnsignedInt(16, params.getTurnaroundRatio().getDenominator());
		} else {
			bos.writeFloat(params.getFrequency());
		}
	}

}
