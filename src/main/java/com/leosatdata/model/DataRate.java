/*
 * Copyright 2026 Andrey Rodionov
 * SPDX-License-Identifier: Apache-2.0
 */
package com.leosatdata.model;

public enum DataRate {

	R2(2, 256, 0b1000), R4(4, 512, 0b1001), R8NC(8, 1, 0b0000), R8C(8, 2, 0b0001), R16(16, 0, 0b1100), R32NC(32, 4, 0b0010), R32C(32, 8, 0b0011), R64(64, 0, 0b1101), R128NC(128, 16, 0b0100), R128C(128, 32, 0b0101), R256NC(256, 64, 0b0110), R256C(256, 128, 0b0111), RESERVED1(0, 1024, 0b1010),
	RESERVED2(0, 2048, 0b1011), RESERVED3(0, 0, 0b1110), RESERVED4(0, 0, 0b1111), UNKNOWN(0, 0, 255);

	private final int code;
	private final int bitsec;
	private final int extendedBitsec;

	private DataRate(int bitsec, int extendedBitsec, int code) {
		this.bitsec = bitsec;
		this.extendedBitsec = extendedBitsec;
		this.code = code;
	}

	public int getCode() {
		return code;
	}

	// return rate in bit/sec
	public int getRate() {
		return bitsec * 1000;
	}

	public int getExtendedRate() {
		return extendedBitsec * 1000;
	}

	public static DataRate valueOfCode(int code) {
		for (DataRate cur : values()) {
			if (cur.code == code) {
				return cur;
			}
		}
		return UNKNOWN;
	}

}
