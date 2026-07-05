package com.leosatdata.service;

import java.util.ArrayList;
import java.util.List;

public class MockTransceiverCallback implements TransceiverCallback {

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
		// do nothing
	}

	@Override
	public void onCarrierAcquired() {
		// do nothing
	}

	@Override
	public void onCarrierLost() {
		// do nothing
	}

	@Override
	public void onSymbolLock() {
		// do nothing
	}

	@Override
	public void onSymbolUnlock() {
		// do nothing
	}

	@Override
	public void onAsmRx() {
		// do nothing
	}

	@Override
	public void onAsmTx() {
		// do nothing
	}
}
