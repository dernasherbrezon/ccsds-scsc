/*
 * Copyright 2026 Andrey Rodionov
 * SPDX-License-Identifier: Apache-2.0
 */
package com.leosatdata.model;

public enum RangingCode {

	MAXIMUM_LENGTH_PN18(0b00), T2B(0b01), T4B(0b10), UNKNOWN(255);

	private final int code;

	private RangingCode(int code) {
		this.code = code;
	}

	public int getCode() {
		return code;
	}

	public static RangingCode valueOfCode(int code) {
		for (RangingCode cur : values()) {
			if (cur.code == code) {
				return cur;
			}
		}
		return UNKNOWN;
	}

}
