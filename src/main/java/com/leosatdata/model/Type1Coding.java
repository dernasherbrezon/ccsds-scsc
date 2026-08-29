/*
 * Copyright 2026 Andrey Rodionov
 * SPDX-License-Identifier: Apache-2.0
 */
package com.leosatdata.model;

public enum Type1Coding {

	LDPC(0b00), CONVOLUTIONAL(0b01), NONE(0b10), CONCANTENATED(0b11), UNKNOWN(255);

	private final int code;

	private Type1Coding(int code) {
		this.code = code;
	}

	public int getCode() {
		return code;
	}

	public static Type1Coding valueOfCode(int code) {
		for (Type1Coding cur : values()) {
			if (cur.code == code) {
				return cur;
			}
		}
		return UNKNOWN;
	}

}
