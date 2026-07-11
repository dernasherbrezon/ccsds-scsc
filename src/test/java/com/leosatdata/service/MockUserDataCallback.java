package com.leosatdata.service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class MockUserDataCallback implements UserDataCallback {

	private List<byte[]> data = new ArrayList<>();
	private ByteArrayOutputStream baos = new ByteArrayOutputStream();
	private final Object lock = new Object();

	@Override
	public void onPacketData(byte[] payload, int portId) {
		synchronized (lock) {
			data.add(payload);
			lock.notifyAll();
		}
	}

	@Override
	public void onPacketStream(byte[] payload, int portId, int gapFramesCount, int firstHeaderPointer) {
		synchronized (lock) {
			try {
				baos.write(payload);
			} catch (IOException e) {
				// ignore
			}
			lock.notifyAll();
		}
	}

	public byte[] waitForData() {
		return waitForData(10000);
	}

	public byte[] waitForStream(int expectedSize) {
		List<byte[]> result = waitForData(expectedSize, 10000);
		if (result.isEmpty()) {
			return null;
		}
		return result.get(0);
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
					if (data.size() >= size || baos.size() >= size) {
						break;
					}
					remaining -= System.currentTimeMillis() - start;
					if (remaining > 0) {
						continue;
					}
				} catch (InterruptedException e) {
					Thread.currentThread().interrupt();
					return Collections.emptyList();
				}
			}
			List<byte[]> result = new ArrayList<>(size);
			if (data.size() > 0) {
				if (data.size() < size) {
					result.addAll(data);
					data.clear();
					return result;
				}
				int i = 0;
				for (; i < size; i++) {
					result.add(data.get(i));
				}
				List<byte[]> remainder = new ArrayList<>();
				for (; i < data.size(); i++) {
					remainder.add(data.get(i));
				}
				data.clear();
				data.addAll(remainder);
			} else {
				result.add(baos.toByteArray());
				baos = new ByteArrayOutputStream();
			}
			return result;
		}
	}

}
