package com.leosatdata.service;

import java.io.ByteArrayOutputStream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.leosatdata.util.Configuration;
import com.leosatdata.util.Crc32c;
import com.leosatdata.util.Util;

public class CsSublayer {

	private final static Logger LOG = LoggerFactory.getLogger(CsSublayer.class);

	private final byte[] asm;
	private final boolean prependAsm;
	private final boolean appendCrc;

	public CsSublayer(Configuration config) {
		this.asm = Util.hexToBytes(config.getProperty("cs.sublayer.asm"));
		this.prependAsm = config.getBoolean("cs.sublayer.asm.prepend");
		this.appendCrc = config.getBoolean("cs.sublayer.crc.append");
	}

	public byte[] encode(byte[] data) {
		if (!prependAsm && !appendCrc) {
			return data;
		}

		ByteArrayOutputStream baos = new ByteArrayOutputStream();
		if (prependAsm) {
			baos.writeBytes(asm);
		}
		baos.writeBytes(data);
		if (appendCrc) {
			long crc = Crc32c.calculate(data);
			baos.write((int) ((crc >>> 24) & 0xFF));
			baos.write((int) ((crc >>> 16) & 0xFF));
			baos.write((int) ((crc >>> 8) & 0xFF));
			baos.write((int) ((crc >>> 0) & 0xFF));
		}
		return baos.toByteArray();
	}

	public byte[] decode(byte[] data) {
		if (!prependAsm && !appendCrc) {
			return data;
		}
		int offset = 0;
		if (prependAsm) {
			if (data.length < asm.length) {
				LOG.debug("invalid pdu length {}", data.length);
				return null;
			}
			if (!checkAsm(data)) {
				LOG.debug("invalid asm");
				return null;
			}
			offset += asm.length;
		}

		int length = data.length;
		if (prependAsm) {
			length -= asm.length;
		}
		if (appendCrc) {
			length -= 4;
		}

		if (length <= 0) {
			LOG.debug("invalid pdu length: {}", data.length);
			return null;
		}

		if (appendCrc) {
			long actualCrc = ((data[data.length - 4] & 0xFFL) << 24) | ((data[data.length - 3] & 0xFFL) << 16) | ((data[data.length - 2] & 0xFFL) << 8) | (data[data.length - 1] & 0xFFL);
			long expectedCrc = Crc32c.calculate(data, offset, length);
			if (actualCrc != expectedCrc) {
				LOG.debug("crc mismatch");
				return null;
			}
		}

		byte[] result = new byte[length];
		System.arraycopy(data, offset, result, 0, length);
		return result;
	}

	private boolean checkAsm(byte[] data) {
		for (int i = 0; i < asm.length; i++) {
			if (asm[i] != data[i]) {
				return false;
			}
		}
		return true;
	}
}
