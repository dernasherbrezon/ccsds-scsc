/*
 * Copyright 2026 Andrey Rodionov
 * SPDX-License-Identifier: Apache-2.0
 */
package com.leosatdata.service;

public interface TransceiverCallback {
	
	void onRx(byte[] data);
	
	void onTx();
	
	void onCarrierAcquired();
	
	void onCarrierLost();
	
	void onSymbolLock();
	
	void onSymbolUnlock();
	
	void onAsmRx();
	
	void onAsmTx();

}
