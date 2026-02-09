package com.leosatdata.service;

import com.leosatdata.ccsds2110B6.model.TransferFrame;

public class Fop implements Runnable {

	private final Transceiver transceiver;
	private Thread t;

	private int vs = 0;
	private int ves = 0;
	private int vvs = 0;
	private int nnr = 0;
	private int nr = 0;
	private boolean rr = false;
	private boolean rrr = false;
	private boolean resync = false;
	private boolean needPlcw = true;
	private boolean needStatusReport = true;

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

	// state SE0
	public void setInitializeMode() {
		vs = 0;
		ves = 0;
		vvs = 0;
		nnr = 0;
		nr = 0;
		rr = false;
		rrr = false;
		resync = false;
		needPlcw = false;
		needStatusReport = false;
	}
}
