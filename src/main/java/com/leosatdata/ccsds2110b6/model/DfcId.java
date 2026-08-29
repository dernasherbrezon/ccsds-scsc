/*
 * Copyright 2026 Andrey Rodionov
 * SPDX-License-Identifier: Apache-2.0
 */
package com.leosatdata.ccsds2110b6.model;

public enum DfcId {

	PACKETS(0b00), SEGMENTED(0b01), UNKNOWN(0b10), USER(0b11);

	private final int code;

	private DfcId(int code) {
		this.code = code;
	}

	public int getCode() {
		return code;
	}

	public static DfcId valueOfCode(int code) {
		for (DfcId cur : values()) {
			if (cur.code == code) {
				return cur;
			}
		}
		return UNKNOWN;
	}

}
