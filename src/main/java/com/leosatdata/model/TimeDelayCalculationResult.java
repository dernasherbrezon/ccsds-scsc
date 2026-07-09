package com.leosatdata.model;

public class TimeDelayCalculationResult {

	private long initialClock;
	private long txAsmClock;
	private long rxAsmClock;

	public long getInitialClock() {
		return initialClock;
	}

	public void setInitialClock(long initialClock) {
		this.initialClock = initialClock;
	}

	public long getTxAsmClock() {
		return txAsmClock;
	}

	public void setTxAsmClock(long txAsmClock) {
		this.txAsmClock = txAsmClock;
	}

	public long getRxAsmClock() {
		return rxAsmClock;
	}

	public void setRxAsmClock(long rxAsmClock) {
		this.rxAsmClock = rxAsmClock;
	}

	public boolean isComplete() {
		return txAsmClock != 0 && rxAsmClock != 0;
	}

}
