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
		if (tx.getTxFrequency() != rx.getRxFrequency()) {
			LOG.error("tx frequency {} doesn't match rx {}", tx.getTxFrequency(), rx.getRxFrequency());
			return false;
		}
		return true;
	}
}
