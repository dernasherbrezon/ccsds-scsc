package com.leosatdata.model;

public class ControlParameters {

	private boolean transmitToken;
	private boolean rnmd; // remote node has no more data to send
	private Duplex duplex;
	private int timeSampleField; // number of frames to sample

	public ControlParameters() {
		// do nothing
	}

	public ControlParameters(ControlParameters other) {
		if (other == null) {
			return;
		}
		this.transmitToken = other.transmitToken;
		this.rnmd = other.rnmd;
		this.duplex = other.duplex;
		this.timeSampleField = other.timeSampleField;
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

	@Override
	public String toString() {
		return "[transmitToken=" + transmitToken + ", rnmd=" + rnmd + ", duplex=" + duplex + ", timeSampleField=" + timeSampleField + "]";
	}

}
