/*
 * Copyright 2026 Andrey Rodionov
 * SPDX-License-Identifier: Apache-2.0
 */
package com.leosatdata.service;

import com.leosatdata.model.CarrierModulation;
import com.leosatdata.model.Coding;
import com.leosatdata.model.Coherency;
import com.leosatdata.model.LineCoding;
import com.leosatdata.model.ModulationIndex;
import com.leosatdata.model.Polarization;
import com.leosatdata.model.ScramblerType;

// i.e PhysicalChannel
public interface Transceiver {

	int send(byte[] data);

	// RX parameters
	int setRxSymbolRate(float baud);
	int setRxLineCoding(LineCoding modulation);
	int setRxFrequency(float frequency);
	int setRxCoding(Coding encoding);
	int setRxPolarization(Polarization polarization);
	int setRxCoherency(Coherency modulation);
	int setRxCarrierModulation(CarrierModulation carrierModulation);
	int setRxModulationIndex(ModulationIndex modulationIndex);
	int setRxScrambler(ScramblerType scrambler);
	int setRxDifferentialEncoding(boolean differentialEncoding);

	// TX parameters
	int setTxSymbolRate(float baud);
	int setTxLineCoding(LineCoding modulation);
	int setTxFrequency(float frequency);
	int setTxCoding(Coding encoding);
	int setTxPolarization(Polarization polarization);
	int setTxCoherency(Coherency modulation);	
	int setTxCarrierModulation(CarrierModulation carrierModulation);
	int setTxModulationIndex(ModulationIndex modulationIndex);
	int setTxScrambler(ScramblerType scrambler);
	int setTxDifferentialEncoding(boolean differentialEncoding);

	// callback
	void setCallback(TransceiverCallback callback);
	TransceiverCallback getCallback();

	// change tx/rx state
	int setTxState(TransceiverTxState state);
	TransceiverTxState getTxState();
	int setRxState(TransceiverRxState state);
	TransceiverRxState getRxState();
	
	// Enable/Disable Rx/Tx ASM callbacks
	// These callbacks used mostly for time tagging purposes and shouldn't be used
	// all the time for performance reasons
	// Separate for ingress and egress messages, because message rate
	// for tx and rx can be different
	int enableAsmRxCallback(boolean enable);
	int enableAsmTxCallback(boolean enable);
}
