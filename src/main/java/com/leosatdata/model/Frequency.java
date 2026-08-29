/*
 * Copyright 2026 Andrey Rodionov
 * SPDX-License-Identifier: Apache-2.0
 */
package com.leosatdata.model;

public enum Frequency {

	CH0(0b000), CH1(0b001), CH2(0b010), CH3(0b011), CH4(0b100), CH5(0b101), CH6(0b110), CH7(0b111), UNKNOWN(255); 

	private final int code;

	private Frequency(int code) {
		this.code = code;
	}

	public int getCode() {
		return code;
	}

	public static Frequency valueOfCode(int code) {
		for (Frequency cur : values()) {
			if (cur.code == code) {
				return cur;
			}
		}
		return UNKNOWN;
	}

}
