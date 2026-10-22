/*
 * Copyright 2026 Andrey Rodionov
 * SPDX-License-Identifier: Apache-2.0
 */
package com.leosatdata.service;

import com.leosatdata.model.CommParameters;

// i.e PhysicalChannel
public interface Transceiver {

	int send(byte[] data);

	int setRxCommParameters(CommParameters params);
	int setTxCommParameters(CommParameters params);

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
