package com.leosatdata.service;

import com.leosatdata.util.Configuration;

public class HalfDuplexTransceiver extends NullTransceiver {

	public HalfDuplexTransceiver(Configuration config) {
		super(config);
	}

	// ensure tx and rx properly switched in tests
	@Override
	public void setTxState(TransceiverTxState state) {
		super.setTxState(state);
		if (!state.equals(TransceiverTxState.OFF)) {
			super.setRxState(TransceiverRxState.OFF);
		}
	}

	@Override
	public void setRxState(TransceiverRxState state) {
		super.setRxState(state);
		if (!state.equals(TransceiverRxState.OFF)) {
			super.setTxState(TransceiverTxState.OFF);
		}
	}
}
