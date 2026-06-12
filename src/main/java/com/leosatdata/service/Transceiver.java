package com.leosatdata.service;

import com.leosatdata.model.Type1CarrierModulation;
import com.leosatdata.model.Coding;
import com.leosatdata.model.LineCoding;
import com.leosatdata.model.Modulation;
import com.leosatdata.model.Polarization;

// i.e PhysicalChannel
public interface Transceiver {

	void send(byte[] data);

	// FIXME status codes?

	// RX parameters
	void setRxSymbolRate(float baud);
	void setRxLineCoding(LineCoding modulation);
	void setRxFrequency(float frequency);
	void setRxDataEncoding(Coding encoding);
	void setRxPolarization(Polarization polarization);
	void setRxModulation(Modulation modulation);
	void setRxCarrierModulation(Type1CarrierModulation carrierModulation);

	void setRxCallback(RxCallback callback);
	RxCallback getRxCallback();

	// TX parameters
	void setTxSymbolRate(float baud);
	void setTxLineCoding(LineCoding modulation);
	void setTxFrequency(float frequency);
	void setTxDataEncoding(Coding encoding);
	void setTxPolarization(Polarization polarization);
	void setTxModulation(Modulation modulation);	
	void setTxCarrierModulation(Type1CarrierModulation carrierModulation);
	
	void setTxCallback(TxCallback callback);
	TxCallback getTxCallback();
	void setCarrierCallback(CarrierCallback callback);
	CarrierCallback getCarrierCallback();
	void setSymbolLockCallback(SymbolLockCallback callback);
	SymbolLockCallback getSymbolLockCallback();

	void setTxState(TransceiverTxState state);
	TransceiverTxState getTxState();
	void setRxState(TransceiverRxState state);
	TransceiverRxState getRxState();


}
