package com.leosatdata.model;

public class Vr {

	private SpduHeader header;
	private int frameSequenceNumber; // SEQ_CTRL_FSN

	public SpduHeader getHeader() {
		return header;
	}

	public void setHeader(SpduHeader header) {
		this.header = header;
	}

	public int getFrameSequenceNumber() {
		return frameSequenceNumber;
	}

	public void setFrameSequenceNumber(int frameSequenceNumber) {
		this.frameSequenceNumber = frameSequenceNumber;
	}

}
