package com.leosatdata.service;

import com.leosatdata.util.Configuration;

public class HalfDuplexTransceiver extends NullTransceiver {

	public HalfDuplexTransceiver(Configuration config) {
		super(config);
	}

	// ensure tx and rx properly switched in tests
	@Override
	public int setTxState(TransceiverTxState state) {
		// switch off receiver first
		if (!state.equals(TransceiverTxState.OFF)) {
			super.setRxState(TransceiverRxState.OFF);
		}
		return super.setTxState(state);
	}

	@Override
	public int setRxState(TransceiverRxState state) {
		// switch off transmitter first
		if (!state.equals(TransceiverRxState.OFF)) {
			super.setTxState(TransceiverTxState.OFF);
		}
		return super.setRxState(state);
	}
}
