package com.leosatdata.model;

public class TransmitterParameters {

	private SpduHeader header;
	private TransmitterMode mode;
	private DataRate dataRate;
	private Modulation modulation;
	private DataEncoding dataEncoding;
	private Frequency frequency;

	public void setHeader(SpduHeader header) {
		this.header = header;
	}

	public SpduHeader getHeader() {
		return header;
	}

	public TransmitterMode getMode() {
		return mode;
	}

	public void setMode(TransmitterMode mode) {
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
