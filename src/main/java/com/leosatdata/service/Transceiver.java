package com.leosatdata.service;

import com.leosatdata.model.Coding;
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

	void stopIdle();

	// RX parameters
	void setRxSymbolRate(float baud);
	void setRxDataModulation(DataModulation modulation);
	void setRxFrequency(float frequency);
	void setRxDataEncoding(Coding encoding);
	void setRxPolarization(Polarization polarization);
	void setRxModulation(Modulation modulation);

	// TX parameters
	void setTxSymbolRate(float baud);
	void setTxDataModulation(DataModulation modulation);
	void setTxFrequency(float frequency);
	void setTxDataEncoding(Coding encoding);
	void setTxPolarization(Polarization polarization);
	void setTxModulation(Modulation modulation);	
	

}
