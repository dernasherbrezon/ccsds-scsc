package com.leosatdata.service;

class DelayedTxTask implements Runnable {

	private final NullTransceiver transceiver;

	public DelayedTxTask(NullTransceiver transceiver) {
		this.transceiver = transceiver;
	}

	@Override
	public void run() {
		if( transceiver.isAsmTxCallback() ) {
			transceiver.getCallback().onAsmTx();
		}
		transceiver.getCallback().onTx();
	}

}