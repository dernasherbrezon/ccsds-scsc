package com.leosatdata.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DefaultFrameFilter implements FrameFilter {

	private final static Logger LOG = LoggerFactory.getLogger(DefaultFrameFilter.class);

	@Override
	public boolean accept(NullTransceiver tx, NullTransceiver rx, byte[] data) {
		if (tx.getTxFrequency() != rx.getRxFrequency()) {
			LOG.error("tx frequency doesn't match rx");
			return false;
		}
		return true;
	}
}
