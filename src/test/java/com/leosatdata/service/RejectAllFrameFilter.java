package com.leosatdata.service;

public class RejectAllFrameFilter implements FrameFilter {

	@Override
	public boolean accept(NullTransceiver tx, NullTransceiver rx, byte[] data) {
		return false;
	}
}
