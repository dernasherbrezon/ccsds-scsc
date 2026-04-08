package com.leosatdata.service;

public interface FrameFilter {

	boolean accept(NullTransceiver tx, NullTransceiver rx, byte[] data);
	
}
