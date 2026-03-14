package com.leosatdata.service;

import com.leosatdata.model.ControllerEvent;

public class MockNodeCallback implements NodeCallback {

	private final Object lock = new Object();
	private ControllerEvent event;

	@Override
	public void handle(ControllerEvent event) {
		synchronized (lock) {
			this.event = event;
			lock.notifyAll();
		}
	}

	public ControllerEvent waitForEvent() {
		synchronized (lock) {
			try {
				// ignore spurious wake-ups
				lock.wait(15000);
				ControllerEvent result = event;
				event = null;
				return result;
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
				return null;
			}
		}
	}

}
