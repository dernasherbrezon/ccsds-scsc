/*
 * Copyright 2026 Andrey Rodionov
 * SPDX-License-Identifier: Apache-2.0
 */
package com.leosatdata.model;

public enum RangingType {

	OFF(0b00), ONE_WAY(0b01), TWO_WAY_NON_REGENERATIVE(0b10), TWO_WAY_REGENERATIVE(0b11), UNKNOWN(255);

	private final int code;

	private RangingType(int code) {
		this.code = code;
	}

	public int getCode() {
		return code;
	}

	public static RangingType valueOfCode(int code) {
		for (RangingType cur : values()) {
			if (cur.code == code) {
				return cur;
			}
		}
		return UNKNOWN;
	}

}
