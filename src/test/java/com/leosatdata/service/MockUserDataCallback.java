package com.leosatdata.service;

import java.util.ArrayList;
import java.util.List;

public class MockUserDataCallback implements UserDataCallback {

	private List<byte[]> data = new ArrayList<>();
	private final Object lock = new Object();
	private boolean collectMulti = false;

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
		List<byte[]> result = waitForData(1, timeout);
		if (result.isEmpty()) {
			return null;
		}
		return result.get(0);
	}

	public List<byte[]> waitForData(int size, long timeout) {
		synchronized (lock) {
			long remaining = timeout;
			while (remaining > 0) {
				long start = System.currentTimeMillis();
				try {
					lock.wait(remaining);
					if (data.size() == size) {
						break;
					}
					remaining = System.currentTimeMillis() - start;
					if (remaining > 0) {
						continue;
					}
				} catch (InterruptedException e) {
					Thread.currentThread().interrupt();
					break;
				}
			}
			List<byte[]> result = new ArrayList<>(data);
			data.clear();
			return result;
		}
	}

	public void setCollectMulti(boolean collectMulti) {
		this.collectMulti = collectMulti;
	}

	public boolean isCollectMulti() {
		return collectMulti;
	}

}
