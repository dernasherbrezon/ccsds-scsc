/*
 * Copyright 2026 Andrey Rodionov
 * SPDX-License-Identifier: Apache-2.0
 */
package com.leosatdata.service;

public class FrameFilterOnce extends DefaultFrameFilter {

	private final String message;
	private boolean filtered = false;

	public FrameFilterOnce(String message) {
		this.message = message;
	}

	@Override
	public boolean accept(NullTransceiver tx, NullTransceiver rx, byte[] data) {
		boolean result = super.accept(tx, rx, data);
		if (!result) {
			return result;
		}
		if (filtered) {
			return true;
		}
		String incoming = new String(data);
		if (incoming.contains(message)) {
			filtered = true;
			return false;
		}
		return true;
	}
}
