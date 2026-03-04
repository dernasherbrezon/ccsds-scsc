package com.leosatdata.service;

public interface SpduCallback {

	void onSpdu(byte[] spdu);
	
	void onSpduTx();

}
