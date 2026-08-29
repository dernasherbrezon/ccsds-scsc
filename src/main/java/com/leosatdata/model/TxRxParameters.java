/*
 * Copyright 2026 Andrey Rodionov
 * SPDX-License-Identifier: Apache-2.0
 */
package com.leosatdata.model;

public class TxRxParameters {

	private Mode mode; // the purpose is unclear
	private DataRate dataRate;
	private Coherency coherency;
	private Type1Coding coding;
	private Frequency frequency;

	public Mode getMode() {
		return mode;
	}

	public void setMode(Mode mode) {
		this.mode = mode;
	}

	public DataRate getDataRate() {
		return dataRate;
	}

	public void setDataRate(DataRate dataRate) {
		this.dataRate = dataRate;
	}

	public Coherency getCoherency() {
		return coherency;
	}

	public void setCoherency(Coherency coherency) {
		this.coherency = coherency;
	}

	public Frequency getFrequency() {
		return frequency;
	}

	public void setFrequency(Frequency frequency) {
		this.frequency = frequency;
	}

	public Type1Coding getCoding() {
		return coding;
	}
	
	public void setCoding(Type1Coding coding) {
		this.coding = coding;
	}

	@Override
	public String toString() {
		return "[mode=" + mode + ", dataRate=" + dataRate + ", coherency=" + coherency + ", coding=" + coding + ", frequency=" + frequency + "]";
	}
}
