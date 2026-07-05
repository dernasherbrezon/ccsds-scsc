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
