package com.leosatdata.util;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

import com.leosatdata.model.Plcw;
import com.leosatdata.model.Spdu;
import com.leosatdata.model.SpduHeader;
import com.leosatdata.model.StatusReport;
import com.leosatdata.model.TimeDistribution;
import com.leosatdata.model.Type1Directive;
import com.leosatdata.model.Type4Directive;
import com.leosatdata.model.Type5Directive;

public class SpduParser {

	private final static int FIXED_SPDU = 1;
	private final static int VARIABLE_SPDU = 0;

	private final static int TYPE1_DIRECTIVE = 0b000;
	private final static int TYPE2_DIRECTIVE = 0b001;
	private final static int TYPE3_DIRECTIVE = 0b010;
	private final static int TYPE4_DIRECTIVE = 0b011;
	private final static int TYPE5_DIRECTIVE = 0b100;

	private final SpduCallback callback;

	public SpduParser(SpduCallback callback) {
		this.callback = callback;
	}

	public static void encode(Spdu message, BitOutputStream bos) throws IOException {
		if (message instanceof Plcw) {
			bos.writeUnsignedInt(1, FIXED_SPDU);
			bos.writeUnsignedInt(1, message.getHeader().getType());
			PlcwParser.encode((Plcw) message, bos);
			return;
		}

		bos.writeUnsignedInt(1, VARIABLE_SPDU);
		bos.writeUnsignedInt(3, message.getHeader().getType());

		ByteArrayOutputStream baos = new ByteArrayOutputStream();
		try (BitOutputStream payload = new BitOutputStream(baos)) {
			if (message instanceof Type1Directive) {
				Type1DirectiveParser.encode((Type1Directive) message, payload);
			} else if (message instanceof TimeDistribution) {
				TimeDistributionParser.encode((TimeDistribution) message, payload);
			} else if (message instanceof StatusReport) {
				StatusReportParser.encode((StatusReport) message, payload);
			} else if (message instanceof Type4Directive) {
				Type4DirectiveParser.encode((Type4Directive) message, payload);
			} else if (message instanceof Type5Directive) {
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

	public void decode(BitInputStream bis) throws IOException {
		SpduHeader header = new SpduHeader();
		header.setFormat(bis.readUnsignedInt(1));
		if (header.getFormat() == FIXED_SPDU) {
			header.setType(bis.readUnsignedInt(1));
			callback.onPlsw(PlcwParser.decode(header, bis));
			return;
		}

		// VARIABLE_SPDU
		header.setType(bis.readUnsignedInt(3));
		int lengthBytes = bis.readUnsignedInt(4);
		switch (header.getType()) {
		case TYPE1_DIRECTIVE:
			callback.onType1Directive(Type1DirectiveParser.decode(header, lengthBytes, bis));
			break;
		case TYPE2_DIRECTIVE:
			callback.onTimeDistribution(TimeDistributionParser.decode(header, bis));
			break;
		case TYPE3_DIRECTIVE:
			callback.onStatusReport(StatusReportParser.decode(header, bis));
			break;
		case TYPE4_DIRECTIVE:
			callback.onType4Directive(Type4DirectiveParser.decode(header, bis));
			break;
		case TYPE5_DIRECTIVE:
			callback.onType5Directive(Type5DirectiveParser.decode(header, bis));
			break;
		default:
			throw new IOException("unknown directive type: " + header.getType());
		}

	}

}
