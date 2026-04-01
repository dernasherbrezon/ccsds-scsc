package com.leosatdata.service;

import java.util.ArrayList;
import java.util.List;

public class MockUserDataCallback implements UserDataCallback {

	private List<byte[]> data = new ArrayList<>();
	private final Object lock = new Object();

	@Override
	public void onUserData(byte[] payload, int portId) {
		synchronized (lock) {
			data.add(payload);
			lock.notifyAll();
		}
	}

	public byte[] waitForData() {
		return waitForData(10000);
	}

	public byte[] waitForData(long timeout) {
		synchronized (lock) {
			try {
				// TODO ignore spurious wake-ups
				lock.wait(timeout);
				if (data.isEmpty()) {
					return null;
				}
				byte[] result = data.get(0);
				data.clear();
				return result;
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
				return null;
			}
		}
	}
}
