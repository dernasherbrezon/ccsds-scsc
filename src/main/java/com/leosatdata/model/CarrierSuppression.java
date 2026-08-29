/*
 * Copyright 2026 Andrey Rodionov
 * SPDX-License-Identifier: Apache-2.0
 */
package com.leosatdata.model;

public enum CarrierSuppression {

	SUPPRESSED_CARRIER, RESIDUAL_CARRIER, RESERVED, UNKNOWN;

	public static CarrierSuppression valueOfCode(int code) {
		switch (code) {
		case 0b00:
			return SUPPRESSED_CARRIER;
		case 0b01:
			return RESIDUAL_CARRIER;
		case 0b10:
		case 0b11:
			return RESERVED;
		default:
			return UNKNOWN;
		}
	}

	public int getCode() {
		switch (this) {
		case SUPPRESSED_CARRIER:
			return 0b00;
		case RESIDUAL_CARRIER:
			return 0b01;
		default:
			return 0b11;
		}
	}

}
