package com.leosatdata.service;

import com.leosatdata.ccsds2110B6.model.TransferFrame;

public class Fop implements Runnable {

	private final Transceiver transceiver;
	private Thread t;

	public Fop(Transceiver transceiver) {
		this.transceiver = transceiver;
		t = new Thread(this, "fop");
	}

	public void start() {
		t.start();
	}

	@Override
	public void run() {
		
		// TODO Auto-generated method stub

	}
	
	public void send(TransferFrame frame) {
		
	}

	public void stop() {
		
	}

}
