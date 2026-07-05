package com.leosatdata.service;

import java.util.ArrayList;
import java.util.List;

public class MockRxCallback implements TransceiverCallback {

	private List<byte[]> frames = new ArrayList<>();

	@Override
	public void onRx(byte[] data) {
		frames.add(data);
	}

	public List<byte[]> getFrames() {
		return frames;
	}

	@Override
	public void onTx() {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void onCarrierAcquired() {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void onCarrierLost() {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void onSymbolLock() {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void onSymbolUnlock() {
		// TODO Auto-generated method stub
		
	}
}
