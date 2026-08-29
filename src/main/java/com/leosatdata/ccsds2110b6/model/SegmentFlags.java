/*
 * Copyright 2026 Andrey Rodionov
 * SPDX-License-Identifier: Apache-2.0
 */
package com.leosatdata.ccsds2110b6.model;

public enum SegmentFlags {

	FIRST(0b01), CONTINUING(0b00), LAST(0b10), NO_SEGMENTATION(0b11), UNKNOWN(255);

	private final int code;

	private SegmentFlags(int code) {
		this.code = code;
	}

	public int getCode() {
		return code;
	}

	public static SegmentFlags valueOfCode(int code) {
		for (SegmentFlags cur : values()) {
			if (cur.code == code) {
				return cur;
			}
		}
		return UNKNOWN;
	}

}
