/*
 * Copyright 2026 Andrey Rodionov
 * SPDX-License-Identifier: Apache-2.0
 */
package com.leosatdata.util;

/**
 * CRC-32 as defined in CCSDS 211.2-B-3, annex C: G(X) = X^32 + X^23 + X^21 + X^11 + X^2 + 1,
 * shift register preset to all zeros, MSB first, no reflection, no final XOR.
 */
public class Crc32 {

	private static final long POLYNOMIAL = 0x00A00805L;
	private static final long[] table = new long[256];

	static {
		for (int i = 0; i < table.length; i++) {
			long c = ((long) i) << 24;
			for (int j = 0; j < 8; j++) {
				c = (c & 0x80000000L) != 0 ? (c << 1) ^ POLYNOMIAL : c << 1;
			}
			table[i] = c & 0xFFFFFFFFL;
		}
	}

	public static long calculate(byte[] data) {
		return calculate(data, 0, data.length);
	}

	public static long calculate(byte[] data, int offset, int length) {
		long c = 0;
		for (int i = offset; i < offset + length; i++) {
			c = (table[(int) (((c >>> 24) ^ (data[i] & 0xFF)) & 0xFF)] ^ (c << 8)) & 0xFFFFFFFFL;
		}
		return c;
	}

	private Crc32() {
		//do nothing
	}
}
