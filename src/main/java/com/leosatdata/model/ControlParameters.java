package com.leosatdata.model;

public class ControlParameters {

	// TODO do we need header here? Or create Type1Directive message with header and
	// list of params
	private SpduHeader header;
	private boolean transmitToken;
	private boolean rnmd; // remote node has no more data to send
	private Duplex duplex;
	private int timeSampleField; // TODO Parse according to reference 6 section 5

	public SpduHeader getHeader() {
		return header;
	}

	public void setHeader(SpduHeader header) {
		this.header = header;
	}

	public boolean isTransmitToken() {
		return transmitToken;
	}

	public void setTransmitToken(boolean transmitToken) {
		this.transmitToken = transmitToken;
	}

	public boolean isRnmd() {
		return rnmd;
	}

	public void setRnmd(boolean rnmd) {
		this.rnmd = rnmd;
	}

	public Duplex getDuplex() {
		return duplex;
	}

	public void setDuplex(Duplex duplex) {
		this.duplex = duplex;
	}

	public int getTimeSampleField() {
		return timeSampleField;
	}

	public void setTimeSampleField(int timeSampleField) {
		this.timeSampleField = timeSampleField;
	}

}
