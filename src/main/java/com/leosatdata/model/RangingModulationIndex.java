/*
 * Copyright 2026 Andrey Rodionov
 * SPDX-License-Identifier: Apache-2.0
 */
package com.leosatdata.model;

public enum RangingModulationIndex {

	D8_75(8.75f, 0b000), D17_5(17.5f, 0b001), D35(35, 0b010), D45(45, 0b011), D60(60, 0b100), D70(70, 0b101), UNKNOWN(0, 255);

	private final float degrees;
	private final int code;

	private RangingModulationIndex(float degrees, int code) {
		this.degrees = degrees;
		this.code = code;
	}

	public int getCode() {
		return code;
	}

	public float getDegrees() {
		return degrees;
	}

	public static RangingModulationIndex valueOfCode(int code) {
		for (RangingModulationIndex cur : values()) {
			if (cur.code == code) {
				return cur;
			}
		}
		return UNKNOWN;
	}

}
