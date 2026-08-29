/*
 * Copyright 2026 Andrey Rodionov
 * SPDX-License-Identifier: Apache-2.0
 */
package com.leosatdata.service;

class DelayedRxTask implements Runnable {

	private final byte[] data;
	private final NullTransceiver transceiver;
	private final FrameFilter filter;

	public DelayedRxTask(NullTransceiver transceiver, byte[] data, FrameFilter filter) {
		this.data = data;
		this.transceiver = transceiver;
		this.filter = filter;
	}

	@Override
	public void run() {
		if (filter == null || filter.accept(null, null, data)) {
			if (transceiver.isAsmRxCallback()) {
				transceiver.getCallback().onAsmRx();
			}
			transceiver.getCallback().onRx(data);
		}
	}

}