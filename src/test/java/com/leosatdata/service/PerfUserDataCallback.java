package com.leosatdata.service;

public class PerfUserDataCallback implements UserDataCallback {
	private long bytesReceived = 0;

	@Override
	public synchronized void onUserData(byte[] payload, int portId) {
		bytesReceived += payload.length;
	}

	public synchronized long getBytesReceived() {
		return bytesReceived;
	}
}
