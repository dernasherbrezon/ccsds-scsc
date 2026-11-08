/*
 * Copyright 2026 Andrey Rodionov
 * SPDX-License-Identifier: Apache-2.0
 */
package com.leosatdata.model;

// B4.5 RECEIVER MODULATION
// E2.2.10 Coherent/Non-coherent
public enum Coherency {

	COHERENT, NON_COHERENT, UNKNOWN;

	public static Coherency valueOfCode(int code) {
		if (code == 0) {
			return COHERENT;
		}
		if (code == 1) {
			return NON_COHERENT;
		}
		return UNKNOWN;
	}

	public int getCode() {
		switch (this) {
		case COHERENT:
			return 0;
		case NON_COHERENT:
			return 1;
		default:
			return 0;
		}
	}

}
