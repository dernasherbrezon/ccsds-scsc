/*
 * Copyright 2026 Andrey Rodionov
 * SPDX-License-Identifier: Apache-2.0
 */
package com.leosatdata.service;

public enum TransceiverTxState {

	OFF(0), CARRIER_ONLY(1), MODULATION(2);

	private final int code;

	private TransceiverTxState(int code) {
		this.code = code;
	}

	public int getCode() {
		return code;
	}

}
