package com.leosatdata.service;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class NullTransceiver implements Transceiver {

	private SendCallback sendCallback;
	private ReceiveCallback receiveCallback;
	private final NullTransceiver otherSide;
	private ScheduledExecutorService executor;

	public NullTransceiver(NullTransceiver otherSide) {
		this.otherSide = otherSide;
	}

	public void start() {
		executor = Executors.newScheduledThreadPool(1);
	}

	public void stop() {
		if (executor == null) {
			return;
		}
		// FIXME gracefull shutdown with timeouts and retry
		executor.shutdown();
	}

	@Override
	public void setAsm(byte[] asm) {
		// TODO Auto-generated method stub

	}

	@Override
	public void send(byte[] data) {
		try {
			// FIXME wait expected baud rate timeout
			Thread.sleep(1000);
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			return;
		}
		getSendCallback().onSent();
		// FIXME expected channel timeout
		executor.schedule(new DelayedTask(otherSide.getReceiveCallback(), data), 10000, TimeUnit.MILLISECONDS);
	}

	@Override
	public void setSendCallback(SendCallback callback) {
		this.sendCallback = callback;
	}

	@Override
	public SendCallback getSendCallback() {
		return sendCallback;
	}

	@Override
	public void setReceiveCallback(ReceiveCallback callback) {
		this.receiveCallback = callback;
	}

	@Override
	public ReceiveCallback getReceiveCallback() {
		return receiveCallback;
	}

	private static class DelayedTask implements Runnable {

		private final byte[] data;
		private final ReceiveCallback callback;

		public DelayedTask(ReceiveCallback callback, byte[] data) {
			this.data = data;
			this.callback = callback;
		}

		@Override
		public void run() {
			callback.onReceive(data);
		}

	}

}
