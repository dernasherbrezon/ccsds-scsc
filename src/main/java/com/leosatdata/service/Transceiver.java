package com.leosatdata.service;

public interface Transceiver {

	void send(byte[] data);

	void setTxCallback(TxCallback callback);

	TxCallback getTxCallback();

	void setRxCallback(RxCallback callback);

	RxCallback getRxCallback();

	// FIXME status codes?
	void startRx();

	void stopRx();

	void startTx();

	void stopTx();

	// When MODULATION=on, the coded symbols are modulated onto the radiated
	// carrier; when MODULATION=off, the radiated output is not modulated (i.e.,
	// carrier only).
	void setModulation(boolean modulation);

	boolean getModulation();
}
