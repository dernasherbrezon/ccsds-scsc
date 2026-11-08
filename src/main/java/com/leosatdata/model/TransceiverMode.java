/*
 * Copyright 2026 Andrey Rodionov
 * SPDX-License-Identifier: Apache-2.0
 */
package com.leosatdata.model;

public enum TransceiverMode {

	MISSION_SPECIFIC(0b00),
	PROXIMITY_1(0b01),
	USLP(0b10),
	AOS(0b011),
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
