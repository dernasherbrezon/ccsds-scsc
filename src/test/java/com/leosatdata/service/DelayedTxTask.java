package com.leosatdata.service;

class DelayedTxTask implements Runnable {

	private final TxCallback callback;

	public DelayedTxTask(TxCallback callback) {
		this.callback = callback;
	}

	@Override
	public void run() {
		callback.onTx();
	}

}