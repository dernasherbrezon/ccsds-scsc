/*
 * Copyright 2026 Andrey Rodionov
 * SPDX-License-Identifier: Apache-2.0
 */
package com.leosatdata.util;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.leosatdata.model.DataRate;
import com.leosatdata.model.ErrorCode;
import com.leosatdata.model.Frequency;
import com.leosatdata.model.Plcw;
import com.leosatdata.model.Spdu;
import com.leosatdata.model.SpduHeader;
import com.leosatdata.model.StatusReport;
import com.leosatdata.model.TimeDistribution;
import com.leosatdata.model.TxRxParameters;
import com.leosatdata.model.Type1Directive;
import com.leosatdata.model.Type4Directive;
import com.leosatdata.model.Type4LinkEstablishment;
import com.leosatdata.model.Type5Directive;
import com.leosatdata.model.Type5LinkEstablishment;

public class SpduParser {

	private static final Logger LOG = LoggerFactory.getLogger(SpduParser.class);

	public final static int FIXED_SPDU = 1;
	public final static int VARIABLE_SPDU = 0;

	public final static int TYPE1_DIRECTIVE = 0b000;
	public final static int TYPE2_DIRECTIVE = 0b001;
	public final static int TYPE3_DIRECTIVE = 0b010;
	public final static int TYPE4_DIRECTIVE = 0b011;
	public final static int TYPE5_DIRECTIVE = 0b100;

	private final int plcwType;

	private final Type4DirectiveParser type4Parser;
	private final Type5DirectiveParser type5Parser;

	public SpduParser(Configuration config) {
		this.plcwType = config.getInteger("maximum_frame_sequence_length", 1) == 1 ? Plcw.SHORT_PLCW : Plcw.LONG_PLCW;
		this.type4Parser = new Type4DirectiveParser(config);
		this.type5Parser = new Type5DirectiveParser(config);
	}

	public void encode(Spdu message, BitOutputStream bos) throws IOException {
		if (message instanceof Plcw) {
			Plcw plcw = (Plcw) message;
			bos.writeUnsignedInt(1, FIXED_SPDU);
			bos.writeUnsignedInt(1, plcwType);
			if (plcwType == Plcw.SHORT_PLCW) {
				bos.writeBoolean(plcw.isRetransmit());
				bos.writeUnsignedInt(1, plcw.getPcid());
				bos.writeUnsignedInt(1, 0); // unused
				bos.writeUnsignedInt(3, plcw.getExpeditedFrameCounter());
				bos.writeUnsignedInt(8, plcw.getReportValue());
			} else {
				bos.writeUnsignedInt(9, 0); // unused
				bos.writeBoolean(plcw.isRetransmit());
				bos.writeUnsignedInt(1, plcw.getPcid());
				bos.writeUnsignedInt(3, plcw.getExpeditedFrameCounter());
				bos.writeUnsignedInt(16, plcw.getReportValue());
			}
			return;
		}

		bos.writeUnsignedInt(1, VARIABLE_SPDU);

		ByteArrayOutputStream baos = new ByteArrayOutputStream();
		try (BitOutputStream payload = new BitOutputStream(baos)) {
			if (message instanceof Type1Directive) {
				bos.writeUnsignedInt(3, TYPE1_DIRECTIVE);
				Type1DirectiveParser.encode((Type1Directive) message, payload);
			} else if (message instanceof TimeDistribution) {
				bos.writeUnsignedInt(3, TYPE2_DIRECTIVE);
				TimeDistributionParser.encode((TimeDistribution) message, payload);
			} else if (message instanceof StatusReport) {
				bos.writeUnsignedInt(3, TYPE3_DIRECTIVE);
				StatusReportParser.encode((StatusReport) message, payload);
			} else if (message instanceof Type4Directive) {
				bos.writeUnsignedInt(3, TYPE4_DIRECTIVE);
				type4Parser.encode((Type4Directive) message, payload);
			} else if (message instanceof Type5Directive) {
				bos.writeUnsignedInt(3, TYPE5_DIRECTIVE);
				type5Parser.encode((Type5Directive) message, payload);
			} else {
				throw new IOException("unknown message type: " + message);
			}
		}

		byte[] payloadData = baos.toByteArray();
		// output length despite directive type
		bos.writeUnsignedInt(4, payloadData.length);
		bos.writeFully(payloadData);
	}

	public Spdu decode(BitInputStream bis) throws IOException {
		SpduHeader header = new SpduHeader();
		header.setFormat(bis.readUnsignedInt(1));
		if (header.getFormat() == FIXED_SPDU) {
			header.setType(bis.readUnsignedInt(1));
			Plcw plcw = new Plcw();
			plcw.setHeader(header);
			if (header.getType() == Plcw.SHORT_PLCW) {
				plcw.setRetransmit(bis.readBoolean());
				plcw.setPcid(bis.readUnsignedInt(1));
				bis.skipBits(1);
				plcw.setExpeditedFrameCounter(bis.readUnsignedInt(3));
				plcw.setReportValue(bis.readUnsignedInt(8));
			} else {
				bis.skipBits(9);
				plcw.setRetransmit(bis.readBoolean());
				plcw.setPcid(bis.readUnsignedInt(1));
				plcw.setExpeditedFrameCounter(bis.readUnsignedInt(3));
				plcw.setReportValue(bis.readUnsignedInt(16));
			}
			return plcw;
		}

		// VARIABLE_SPDU
		header.setType(bis.readUnsignedInt(3));
		int lengthBytes = bis.readUnsignedInt(4);
		Spdu result;
		switch (header.getType()) {
		case TYPE1_DIRECTIVE:
			result = Type1DirectiveParser.decode(lengthBytes, bis);
			break;
		case TYPE2_DIRECTIVE:
			result = TimeDistributionParser.decode(bis);
			break;
		case TYPE3_DIRECTIVE:
			result = StatusReportParser.decode(bis);
			break;
		case TYPE4_DIRECTIVE:
			result = type4Parser.decode(bis);
			break;
		case TYPE5_DIRECTIVE:
			result = type5Parser.decode(bis);
			break;
		default:
			LOG.error("unknown directive type: {}", header.getType());
			return null;
		}
		result.setHeader(header);
		return result;

	}

	public int validateCommParameters(Type1Directive obj) {
		if (obj.getTxParams() == null || obj.getRxParams() == null) {
			return ErrorCode.INVALID_ARGUMENT.ordinal();
		}
		int code = validate(obj.getTxParams());
		if (code != 0) {
			return code;
		}
		return validate(obj.getRxParams());
	}

	private static int validate(TxRxParameters params) {
		if (params.getDataRate() == null || params.getDataRate().equals(DataRate.UNKNOWN)) {
			return ErrorCode.INVALID_ARGUMENT.ordinal();
		}
		if (params.getFrequency() == null || params.getFrequency().equals(Frequency.UNKNOWN)) {
			return ErrorCode.INVALID_ARGUMENT.ordinal();
		}
		return ErrorCode.SUCCESS.ordinal();
	}

	public int validateCommParameters(Type4Directive obj) {
		if (obj.getTxLink() == null || obj.getRxLink() == null) {
			return ErrorCode.INVALID_ARGUMENT.ordinal();
		}
		int code = validate(obj.getTxLink());
		if (code != 0) {
			return code;
		}
		return validate(obj.getRxLink());
	}

	private static int validate(Type4LinkEstablishment params) {
		if (params.getFreqChannel() < 0 || params.getFreqChannel() > 31) {
			return 1;
		}
		if (params.getSymbolRate() < 0) {
			return ErrorCode.INVALID_ARGUMENT_LENGTH.ordinal();
		}
		return ErrorCode.SUCCESS.ordinal();
	}

	public int validateCommParameters(Type5Directive obj) {
		if (obj.getTxLink() == null || obj.getRxLink() == null) {
			return ErrorCode.INVALID_ARGUMENT.ordinal();
		}
		int code = validate(obj.getTxLink());
		if (code != 0) {
			return code;
		}
		return validate(obj.getRxLink());
	}

	private static int validate(Type5LinkEstablishment params) {
		if (params.getAosFrameLength() < 0 || params.getAosFrameLength() > ((1 << 16) - 1)) {
			return ErrorCode.INVALID_ARGUMENT_LENGTH.ordinal();
		}
		if (params.getSymbolRate() < 0) {
			return ErrorCode.INVALID_ARGUMENT_LENGTH.ordinal();
		}
		if (params.getFrequency() < 0) {
			return ErrorCode.INVALID_ARGUMENT_LENGTH.ordinal();
		}
		return ErrorCode.SUCCESS.ordinal();
	}

}
