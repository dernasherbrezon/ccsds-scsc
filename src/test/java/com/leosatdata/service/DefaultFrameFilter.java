/*
 * Copyright 2026 Andrey Rodionov
 * SPDX-License-Identifier: Apache-2.0
 */
package com.leosatdata.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DefaultFrameFilter implements FrameFilter {

	private final static Logger LOG = LoggerFactory.getLogger(DefaultFrameFilter.class);

	@Override
	public boolean accept(NullTransceiver tx, NullTransceiver rx, byte[] data) {
		if (!tx.getTxState().equals(TransceiverTxState.MODULATION)) {
			LOG.error("invalid tx state: {}, expected: {}", tx.getTxState(), TransceiverTxState.MODULATION);
			return false;
		}
		if (!rx.getRxState().equals(TransceiverRxState.RECEIVING)) {
			LOG.error("invalid rx state: {}, expected {}", rx.getRxState(), TransceiverRxState.RECEIVING);
			return false;
		}
		if (tx.getTx().getFrequency() != rx.getRx().getFrequency()) {
			LOG.error("tx frequency {} doesn't match rx {}", tx.getTx().getFrequency(), rx.getRx().getFrequency());
			return false;
		}
		if (tx.getTx().getSymbolRate() != rx.getRx().getSymbolRate()) {
			LOG.error("tx symbold rate {} doesn't match rx symbol rate {}", tx.getTx().getSymbolRate(), rx.getRx().getSymbolRate());
			return false;
		}
		return true;
	}
}
