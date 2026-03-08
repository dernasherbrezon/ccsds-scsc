package com.leosatdata.util;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.leosatdata.model.Plcw;
import com.leosatdata.model.Spdu;
import com.leosatdata.model.SpduHeader;
import com.leosatdata.model.StatusReport;
import com.leosatdata.model.TimeDistribution;
import com.leosatdata.model.Type1Directive;
import com.leosatdata.model.Type4Directive;
import com.leosatdata.model.Type5Directive;

public class SpduParser {

	private static final Logger LOG = LoggerFactory.getLogger(SpduParser.class);

	public final static int FIXED_SPDU = 1;
	public final static int VARIABLE_SPDU = 0;

	public final static int TYPE1_DIRECTIVE = 0b000;
	public final static int TYPE2_DIRECTIVE = 0b001;
	public final static int TYPE3_DIRECTIVE = 0b010;
	public final static int TYPE4_DIRECTIVE = 0b011;
	public final static int TYPE5_DIRECTIVE = 0b100;

	private final int spduVersion;

	public SpduParser(Configuration config) {
		this.spduVersion = config.getInteger("spdu_version", 1);
	}

	public void encode(Spdu message, BitOutputStream bos) throws IOException {
		if (message instanceof Plcw) {
			bos.writeUnsignedInt(1, FIXED_SPDU);
			if (spduVersion == Type1Directive.SPDU_VERSION) {
				bos.writeUnsignedInt(1, Plcw.SHORT_PLCW);
			} else {
				bos.writeUnsignedInt(1, Plcw.LONG_PLCW);
			}
			PlcwParser.encode((Plcw) message, spduVersion, bos);
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
				Type4DirectiveParser.encode((Type4Directive) message, payload);
			} else if (message instanceof Type5Directive) {
				bos.writeUnsignedInt(3, TYPE5_DIRECTIVE);
				Type5DirectiveParser.encode((Type5Directive) message, payload);
			} else {
				throw new IOException("unknown message type: " + message);
			}
		}

		byte[] payloadData = baos.toByteArray();
		// output length despite directive type
		bos.writeUnsignedInt(4, payloadData.length);
		bos.writeFully(payloadData);
	}

	public Object decode(BitInputStream bis) throws IOException {
		SpduHeader header = new SpduHeader();
		header.setFormat(bis.readUnsignedInt(1));
		if (header.getFormat() == FIXED_SPDU) {
			header.setType(bis.readUnsignedInt(1));
			return PlcwParser.decode(header, bis);
		}

		// VARIABLE_SPDU
		header.setType(bis.readUnsignedInt(3));
		int lengthBytes = bis.readUnsignedInt(4);
		switch (header.getType()) {
		case TYPE1_DIRECTIVE:
			return Type1DirectiveParser.decode(header, lengthBytes, bis);
		case TYPE2_DIRECTIVE:
			return TimeDistributionParser.decode(header, bis);
		case TYPE3_DIRECTIVE:
			return StatusReportParser.decode(header, bis);
		case TYPE4_DIRECTIVE:
			return Type4DirectiveParser.decode(header, bis);
		case TYPE5_DIRECTIVE:
			return Type5DirectiveParser.decode(header, bis);
		default:
			LOG.error("unknown directive type: {}", header.getType());
			return null;
		}

	}

}
