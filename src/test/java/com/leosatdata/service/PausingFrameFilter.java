package com.leosatdata.service;

import java.util.concurrent.CountDownLatch;

public class PausingFrameFilter extends DefaultFrameFilter {

	private final CountDownLatch latch = new CountDownLatch(1);

	@Override
	public boolean accept(NullTransceiver tx, NullTransceiver rx, byte[] data) {
		boolean result = super.accept(tx, rx, data);
		try {
			latch.await();
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
		}
		return result;
	}

	public void resume() {
		latch.countDown();
	}
}
