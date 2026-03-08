package com.leosatdata.service;

import com.leosatdata.model.Coding;
import com.leosatdata.model.DataModulation;
import com.leosatdata.model.Modulation;
import com.leosatdata.model.Polarization;

public interface Transceiver {

	void send(byte[] data);

	// FIXME status codes?

	// RX parameters
	void setRxSymbolRate(float baud);
	void setRxDataModulation(DataModulation modulation);
	void setRxFrequency(float frequency);
	void setRxDataEncoding(Coding encoding);
	void setRxPolarization(Polarization polarization);
	void setRxModulation(Modulation modulation);

	void setRxCallback(RxCallback callback);
	RxCallback getRxCallback();

	void startRx();
	void stopRx();

	// TX parameters
	void setTxSymbolRate(float baud);
	void setTxDataModulation(DataModulation modulation);
	void setTxFrequency(float frequency);
	void setTxDataEncoding(Coding encoding);
	void setTxPolarization(Polarization polarization);
	void setTxModulation(Modulation modulation);	
	
	void setTxCallback(TxCallback callback);
	TxCallback getTxCallback();

	void startTx();
	void startIdle();
	void stopIdle();
	void stopTx();

}
