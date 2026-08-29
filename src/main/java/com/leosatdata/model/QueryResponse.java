/*
 * Copyright 2026 Andrey Rodionov
 * SPDX-License-Identifier: Apache-2.0
 */
package com.leosatdata.model;

public enum QueryResponse {

	ACK, NACK, UNKNOWN;

	public static QueryResponse valueOfCode(int code) {
		if (code == 0) {
			return ACK;
		} else if (code == 1) {
			return NACK;
		}
		return UNKNOWN;
	}

	public int getCode() {
		if (this == ACK) {
			return 0;
		} else {
			return 1;
		}
	}

}
