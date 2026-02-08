package com.leosatdata.service;

public interface Transceiver {
	
	void setAsm(byte[] asm);
	
	void send(byte[] data);
	
	void setSendCallback(SendCallback callback);
	
	SendCallback getSendCallback();
	
	void setReceiveCallback(ReceiveCallback callback);
	
	ReceiveCallback getReceiveCallback();
	
}
