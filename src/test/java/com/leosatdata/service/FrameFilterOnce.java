package com.leosatdata.service;

public class FrameFilterOnce implements FrameFilter {

	private final String message;
	private boolean filtered = false;

	public FrameFilterOnce(String message) {
		this.message = message;
	}

	@Override
	public boolean accept(byte[] data) {
		if (filtered) {
			return true;
		}
		String incoming = new String(data);
		if (incoming.contains(message)) {
			filtered = true;
			return false;
		}
		return true;
	}
}
