package com.leosatdata.service;

import com.leosatdata.model.MacDuplex;
import com.leosatdata.model.MacMode;
import com.leosatdata.model.PlExtensions;
import com.leosatdata.model.TxRxParameters;
import com.leosatdata.util.Configuration;

public class Mac {

	private final Fop fop;
	private final Farm farm;
	private final Transceiver transceiver;
	private final Configuration config;
	private final NodeCallback callback;

	private MacMode mode = MacMode.INACTIVE;
	private boolean transmit = false;
	private boolean persistence = false;
	private MacDuplex duplex = MacDuplex.FULL; // FIXME check defaults
	private int ss;
	private int x;
	private int y;
	private int z;

	public Mac(Configuration config, Transceiver transceiver, NodeCallback callback) {
		this.config = config;
		this.callback = callback;
		this.transceiver = transceiver;
		this.fop = new Fop(transceiver);
		this.farm = new Farm();
	}

//	public void start() {
//		fop.start();
//		// FIXME start fop / farm
//	}
//
//	public void send(TransferFrame frame) {
//		if (frame == null || frame.getHeader() == null) {
//			return;
//		}
//		// accept only user data from users
//		if (frame.getHeader().getPduTypeId().equals(PduTypeId.SPDU)) {
//			// FIXME some error reporting?
//			return;
//		}
//		fop.send(frame);
//	}
//
//	public void stop() {
//		fop.stop();
//	}

	// SET MODE directive
	public void setMode(MacMode mode) {
		switch (mode) {
		case INACTIVE:
			transceiver.setModulation(false);
			transceiver.stopRx();
			transceiver.stopTx();
			transmit = false;
			persistence = false;
			ss = 0;
			x = 0;
			y = 0;
			z = 0;
			// cancel all timers
			// reset sequence counters
			break;
		case CONNECTING_T:
			transceiver.startTx();
			transceiver.startRx();
			break;
		case CONNECTING_L:
			transceiver.startRx();
			transceiver.stopTx();
			break;
		// FIXME active?
		default:
			break;
		}
		this.mode = mode;
	}

	// SET INITIALIZE MODE directive
	public void setInitializeMode() {
		setMode(MacMode.INACTIVE);
		fop.setInitializeMode();
		farm.setInitializeMode();
	}

	public void setLocalCommChange(TxRxParameters tx, TxRxParameters rx, PlExtensions ext) {

	}

	public void setDuplex(MacDuplex duplex) {
		this.duplex = duplex;
	}
}
