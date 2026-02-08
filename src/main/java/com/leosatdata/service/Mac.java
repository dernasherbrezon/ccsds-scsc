package com.leosatdata.service;

import com.leosatdata.model.MacMode;
import com.leosatdata.util.Configuration;

public class Mac {

	private final Fop fop;
	private final Farm farm;
	private final Configuration config;
	private final NodeCallback callback;

	private MacMode mode = MacMode.INACTIVE;

	public Mac(Configuration config, Transceiver transceiver, NodeCallback callback) {
		this.config = config;
		this.callback = callback;
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

	public void setMode(MacMode mode) {
		this.mode = mode;
		// FIXME call actions
	}
}
