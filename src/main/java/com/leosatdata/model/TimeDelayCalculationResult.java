package com.leosatdata.model;

public class TimeDelayCalculationResult {

	private long initialTimeMillis;
	private long txAsmTimeMillis;
	private long rxAsmTimeMillis;

	public long getInitialTimeMillis() {
		return initialTimeMillis;
	}

	public void setInitialTimeMillis(long initialTimeMillis) {
		this.initialTimeMillis = initialTimeMillis;
	}

	public long getTxAsmTimeMillis() {
		return txAsmTimeMillis;
	}

	public void setTxAsmTimeMillis(long txAsmTimeMillis) {
		this.txAsmTimeMillis = txAsmTimeMillis;
	}

	public long getRxAsmTimeMillis() {
		return rxAsmTimeMillis;
	}

	public void setRxAsmTimeMillis(long rxAsmTimeMillis) {
		this.rxAsmTimeMillis = rxAsmTimeMillis;
	}

	public boolean isComplete() {
		return txAsmTimeMillis != 0 && rxAsmTimeMillis != 0;
	}

}
