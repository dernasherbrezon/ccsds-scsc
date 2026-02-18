package com.leosatdata.model;

public class TxRxParameters {

	private Mode mode;
	private DataRate dataRate;
	private Modulation modulation;
	private DataEncoding dataEncoding;
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

	public Modulation getModulation() {
		return modulation;
	}

	public void setModulation(Modulation modulation) {
		this.modulation = modulation;
	}

	public Frequency getFrequency() {
		return frequency;
	}

	public void setFrequency(Frequency frequency) {
		this.frequency = frequency;
	}

	public DataEncoding getDataEncoding() {
		return dataEncoding;
	}
	
	public void setDataEncoding(DataEncoding dataEncoding) {
		this.dataEncoding = dataEncoding;
	}
}
