package com.leosatdata.model;

public class Vr {

	private int frameSequenceNumber; // SEQ_CTRL_FSN

	public int getFrameSequenceNumber() {
		return frameSequenceNumber;
	}

	public void setFrameSequenceNumber(int frameSequenceNumber) {
		this.frameSequenceNumber = frameSequenceNumber;
	}

	@Override
	public String toString() {
		return "[frameSequenceNumber=" + frameSequenceNumber + "]";
	}

}
