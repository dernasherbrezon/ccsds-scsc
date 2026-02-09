package com.leosatdata.service;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class NullTransceiver implements Transceiver {

	private TxCallback sendCallback;
	private RxCallback receiveCallback;
	private final NullTransceiver otherSide;
	private ScheduledExecutorService executor;
	private boolean rx = false;
	private boolean tx = false;
	private boolean modulation = false;

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
	public void send(byte[] data) {
		assert (tx == true);
		try {
			// FIXME wait expected baud rate timeout
			Thread.sleep(1000);
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			return;
		}
		getTxCallback().onTx();
		// FIXME expected channel timeout
		assert (otherSide.rx == true);
		executor.schedule(new DelayedTask(otherSide.getRxCallback(), data), 10000, TimeUnit.MILLISECONDS);
	}

	@Override
	public void setTxCallback(TxCallback callback) {
		this.sendCallback = callback;
	}

	@Override
	public TxCallback getTxCallback() {
		return sendCallback;
	}

	@Override
	public void setRxCallback(RxCallback callback) {
		this.receiveCallback = callback;
	}

	@Override
	public RxCallback getRxCallback() {
		return receiveCallback;
	}

	private static class DelayedTask implements Runnable {

		private final byte[] data;
		private final RxCallback callback;

		public DelayedTask(RxCallback callback, byte[] data) {
			this.data = data;
			this.callback = callback;
		}

		@Override
		public void run() {
			callback.onRx(data);
		}

	}

	@Override
	public void startRx() {
		rx = true;
	}

	@Override
	public void stopRx() {
		rx = false;
	}

	@Override
	public void startTx() {
		tx = true;
	}

	@Override
	public void stopTx() {
		tx = false;
	}

	@Override
	public void setModulation(boolean modulation) {
		this.modulation = modulation;
	}

	@Override
	public boolean getModulation() {
		return modulation;
	}

}
