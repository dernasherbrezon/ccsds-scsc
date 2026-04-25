package com.leosatdata.service;

import java.io.IOException;

import com.leosatdata.ccsds2110b6.model.PduTypeId;
import com.leosatdata.model.Spdu;
import com.leosatdata.util.BitInputStream;
import com.leosatdata.util.Configuration;
import com.leosatdata.util.SpduParser;
import com.leosatdata.util.TransferFrameUtil;

public abstract class ParsedFrameFilter implements FrameFilter {

	private final SpduParser spduParser;
	private final CsSublayer csSublayer;

	public ParsedFrameFilter(Configuration config) {
		spduParser = new SpduParser(config);
		csSublayer = new CsSublayer(config);
	}

	@Override
	public boolean accept(NullTransceiver tx, NullTransceiver rx, byte[] data) {
		Object frame = parseFrame(csSublayer.decode(data));
		if (frame != null) {
			PduTypeId type = TransferFrameUtil.getPduTypeId(frame);
			if (type.equals(PduTypeId.SPDU)) {
				return onSpdu(parseSpdu(TransferFrameUtil.getPayload(frame)));
			} else {
				return onUserData(frame);
			}
		}
		return true;
	}

	abstract boolean onSpdu(Spdu spdu);

	abstract boolean onUserData(Object frame);

	private static Object parseFrame(byte[] data) {
		try (BitInputStream bis = new BitInputStream(data)) {
			return com.leosatdata.ccsds2110b6.util.TransferFrameParser.decode(bis);
		} catch (IOException e) {
			return null;
		}
	}

	private Spdu parseSpdu(byte[] data) {
		try (BitInputStream bis = new BitInputStream(data)) {
			return spduParser.decode(bis);
		} catch (IOException e) {
			return null;
		}
	}
}
