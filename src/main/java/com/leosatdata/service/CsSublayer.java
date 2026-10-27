/*
 * Copyright 2026 Andrey Rodionov
 * SPDX-License-Identifier: Apache-2.0
 */
package com.leosatdata.service;

import java.io.ByteArrayOutputStream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.leosatdata.util.Configuration;
import com.leosatdata.util.Crc32c;
import com.leosatdata.util.Util;

public class CsSublayer {

	private final static Logger LOG = LoggerFactory.getLogger(CsSublayer.class);
	private final static Logger RX = LoggerFactory.getLogger("rx");
	private final static Logger TX = LoggerFactory.getLogger("tx");

	private final byte[] asm;
	private final boolean prependAsm;
	private final boolean appendCrc;
	private final int maxFrameLength;

	public CsSublayer(Configuration config) {
		this.asm = Util.hexToBytes(config.getProperty("cs.sublayer.asm"));
		this.prependAsm = config.getBoolean("cs.sublayer.asm.prepend");
		this.appendCrc = config.getBoolean("cs.sublayer.crc.append");
		this.maxFrameLength = config.getInteger("maximum_frame_length", 0);
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
		byte[] result = baos.toByteArray();
		if (TX.isDebugEnabled()) {
			TX.debug(Util.bytesToHex(result));
		}
		return result;
	}

	public byte[] decode(byte[] data) {
		if (!prependAsm && !appendCrc) {
			if (RX.isDebugEnabled()) {
				RX.debug(Util.bytesToHex(data));
			}
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

		int length = getLength(data);
		// corrupted frame
		if (length == -1) {
			return null;
		}
		if (appendCrc) {
			if (data.length < offset + length + 4) {
				return null;
			}
			long actualCrc = ((data[offset + length] & 0xFFL) << 24) | ((data[offset + length + 1] & 0xFFL) << 16) | ((data[offset + length + 2] & 0xFFL) << 8) | (data[offset + length + 3] & 0xFFL);
			long expectedCrc = Crc32c.calculate(data, offset, length);
			if (actualCrc != expectedCrc) {
				LOG.debug("crc mismatch");
				return null;
			}
		}
		if (RX.isDebugEnabled()) {
			RX.debug(Util.bytesToHex(data));
		}
		byte[] result = new byte[length];
		System.arraycopy(data, offset, result, 0, length);
		return result;
	}

	private int getLength(byte[] data) {
		int offset = 0;
		if (prependAsm) {
			offset = asm.length;
		}
		if (com.leosatdata.ccsds7320b4.util.TransferFrameParser.accept(data, offset)) {
			// aos frames are fixed length. frame length is header + data
			return maxFrameLength;
		}
		if (com.leosatdata.ccsds2110b6.util.TransferFrameParser.accept(data, offset)) {
			int result = com.leosatdata.ccsds2110b6.util.TransferFrameParser.decodeFrameLength(data, offset);
			// quick discard of the data
			if (result > maxFrameLength) {
				return -1;
			}
			return result;
		}
		if (com.leosatdata.ccsds7321b3.util.TransferFrameParser.accept(data, offset)) {
			int result = com.leosatdata.ccsds7321b3.util.TransferFrameParser.decodeFrameLength(data, offset);
			// quick discard of the data
			if (result > maxFrameLength) {
				return -1;
			}
			return result;
		}
		return -1;
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
