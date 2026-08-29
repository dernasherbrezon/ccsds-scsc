/*
 * Copyright 2026 Andrey Rodionov
 * SPDX-License-Identifier: Apache-2.0
 */
package com.leosatdata.model;

public enum LinkDirection {

	RETURN("r"), FORWARD("f"), UNKNOWN("");

	private final String shortCode;

	private LinkDirection(String shortCode) {
		this.shortCode = shortCode;
	}

	public String getShortCode() {
		return shortCode;
	}

	public static LinkDirection valueOfCode(int code) {
		if (code == 0) {
			return RETURN;
		} else if (code == 1) {
			return FORWARD;
		}
		return UNKNOWN;
	}

	public int getCode() {
		if (this == RETURN) {
			return 0;
		} else {
			return 1;
		}
	}

}
