package com.leosatdata.service;

import java.util.ArrayList;
import java.util.List;

public class MockRxCallback implements RxCallback {

	private List<byte[]> frames = new ArrayList<>();

	@Override
	public void onRx(byte[] data) {
		frames.add(data);
	}

	public List<byte[]> getFrames() {
		return frames;
	}
}
