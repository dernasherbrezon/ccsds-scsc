package com.leosatdata.service;

import com.leosatdata.model.CarrierModulation;
import com.leosatdata.model.Coding;
import com.leosatdata.model.Coherency;
import com.leosatdata.model.LineCoding;
import com.leosatdata.model.ModulationIndex;
import com.leosatdata.model.Polarization;

// i.e PhysicalChannel
public interface Transceiver {

	void send(byte[] data);

	// FIXME status codes?

	// RX parameters
	void setRxSymbolRate(float baud);
	void setRxLineCoding(LineCoding modulation);
	void setRxFrequency(float frequency);
	void setRxCoding(Coding encoding);
	void setRxPolarization(Polarization polarization);
	void setRxCoherency(Coherency modulation);
	void setRxCarrierModulation(CarrierModulation carrierModulation);
	void setRxModulationIndex(ModulationIndex modulationIndex);

	void setRxCallback(RxCallback callback);
	RxCallback getRxCallback();

	// TX parameters
	void setTxSymbolRate(float baud);
	void setTxLineCoding(LineCoding modulation);
	void setTxFrequency(float frequency);
	void setTxCoding(Coding encoding);
	void setTxPolarization(Polarization polarization);
	void setTxCoherency(Coherency modulation);	
	void setTxCarrierModulation(CarrierModulation carrierModulation);
	void setTxModulationIndex(ModulationIndex modulationIndex);

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
