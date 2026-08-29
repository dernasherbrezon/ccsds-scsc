/*
 * Copyright 2026 Andrey Rodionov
 * SPDX-License-Identifier: Apache-2.0
 */
package com.leosatdata.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.leosatdata.model.ErrorCode;

public class Util {

	private static final Logger LOG = LoggerFactory.getLogger(Util.class);
	private static final char[] hexArray = "0123456789ABCDEF".toCharArray();

	public static String bytesToHex(byte[] bytes) {
		char[] hexChars = new char[bytes.length * 2];
		for (int j = 0; j < bytes.length; j++) {
			int v = bytes[j] & 0xFF;
			hexChars[j * 2] = hexArray[v >>> 4];
			hexChars[j * 2 + 1] = hexArray[v & 0x0F];
		}
		return new String(hexChars);
	}

	public static byte[] hexToBytes(String s) {
		int len = 0;
		for (int i = 0; i < s.length(); i++) {
			if (s.charAt(i) == ' ') {
				continue;
			}
			len++;
		}
		byte[] data = new byte[len / 2];
		int index = 0;
		for (int i = 0; i < s.length();) {
			if (s.charAt(i) == ' ') {
				i++;
				continue;
			}
			data[index] = (byte) ((Character.digit(s.charAt(i), 16) << 4) + Character.digit(s.charAt(i + 1), 16));
			i += 2;
			index++;
		}
		return data;
	}

	public static int validateInt(int numberOfBits, String fieldName, int value) {
		int maxValue = (1 << numberOfBits) - 1;
		if (maxValue < value) {
			LOG.error("invalid " + fieldName + ": " + value + " maxValue: " + maxValue);
			return ErrorCode.INVALID_ARGUMENT_LENGTH.ordinal();
		}
		return ErrorCode.SUCCESS.ordinal();
	}

	private Util() {
		// do nothing
	}
}
