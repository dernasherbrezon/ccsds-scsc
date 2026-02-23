package com.leosatdata.service;

public interface RxCallback {
	
	void onRx(byte[] data);
	
	void onCarrierAcquired();
	
	void onSymbolInLock();

}
