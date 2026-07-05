package com.leosatdata.service;

class DelayedTxTask implements Runnable {

	private final TransceiverCallback callback;

	public DelayedTxTask(TransceiverCallback callback) {
		this.callback = callback;
	}

	@Override
	public void run() {
		callback.onTx();
	}

}