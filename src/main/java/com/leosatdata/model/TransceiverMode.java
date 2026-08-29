/*
 * Copyright 2026 Andrey Rodionov
 * SPDX-License-Identifier: Apache-2.0
 */
package com.leosatdata.model;

public enum TransceiverMode {

	PROXIMITY_1(0b000),
	USLP(0b001),
	AOS_SLICED(0b010),
	AOS_NON_SLICED(0b011),
	UNKNOWN(255);

	private final int code;

	private TransceiverMode(int code) {
		this.code = code;
	}

	public int getCode() {
		return code;
	}

	public static TransceiverMode valueOfCode(int code) {
		for (TransceiverMode cur : values()) {
			if (cur.code == code) {
				return cur;
			}
		}
		return UNKNOWN;
	}

}
