/*
 * Copyright 2026 Andrey Rodionov
 * SPDX-License-Identifier: Apache-2.0
 */
package com.leosatdata.model;

public enum Polarization {

	LHCP, RHCP, UNKNOWN;

	public static Polarization valueOfCode(int code) {
		if (code == 0) {
			return LHCP;
		} else if (code == 1) {
			return RHCP;
		}
		return UNKNOWN;
	}

	public int getCode() {
		if (this == LHCP) {
			return 0;
		} else {
			return 1;
		}
	}

}
