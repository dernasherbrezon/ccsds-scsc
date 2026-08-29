/*
 * Copyright 2026 Andrey Rodionov
 * SPDX-License-Identifier: Apache-2.0
 */
package com.leosatdata.service;

public class PerfUserDataCallback implements UserDataCallback {
	private long bytesReceived = 0;

	@Override
	public synchronized void onPacketData(byte[] payload, int portId) {
		bytesReceived += payload.length;
	}

	@Override
	public void onPacketStream(byte[] data, int portId, int gapFramesCount, int firstHeaderPointer) {
		bytesReceived += data.length;
	}

	public synchronized long getBytesReceived() {
		return bytesReceived;
	}
}
