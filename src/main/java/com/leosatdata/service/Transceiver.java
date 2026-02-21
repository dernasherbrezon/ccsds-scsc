package com.leosatdata.service;

import com.leosatdata.model.DataEncoding;
import com.leosatdata.model.DataModulation;
import com.leosatdata.model.Modulation;
import com.leosatdata.model.Polarization;

public interface Transceiver {

	void send(byte[] data);

	void setTxCallback(TxCallback callback);

	TxCallback getTxCallback();

	void setRxCallback(RxCallback callback);

	RxCallback getRxCallback();

	// FIXME status codes?
	void startRx();

	void stopRx();

	void startCarrier();

	void stopTx();

	// When MODULATION=on, the coded symbols are modulated onto the radiated
	// carrier; when MODULATION=off, the radiated output is not modulated (i.e.,
	// carrier only).
	void setModulation(boolean modulation);

	boolean getModulation();

	void startIdle();

	void setSymbolRate(int baud);

	void setDataModulation(DataModulation manchester);

	void setFrequency(long frequency);

	void setDataEncoding(DataEncoding none);

	void setPolarization(Polarization rhcp);

	void setModulation(Modulation nonCoherentPsk);

	void stopIdle();

}
