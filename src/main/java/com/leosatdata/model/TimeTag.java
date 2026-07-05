package com.leosatdata.model;

import com.leosatdata.ccsds2110b6.model.QualityOfService;

public class TimeTag {

	private long time;
	private int frameSequenceNumber;
	private QualityOfService qos;
	private TimeTagType type;

	public TimeTag() {
		// do nothing
	}

	public TimeTag(long time, int frameSequenceNumber, QualityOfService qos, TimeTagType type) {
		this.time = time;
		this.frameSequenceNumber = frameSequenceNumber;
		this.qos = qos;
		this.type = type;
	}

	public long getTime() {
		return time;
	}

	public void setTime(long time) {
		this.time = time;
	}

	public int getFrameSequenceNumber() {
		return frameSequenceNumber;
	}

	public void setFrameSequenceNumber(int frameSequenceNumber) {
		this.frameSequenceNumber = frameSequenceNumber;
	}

	public QualityOfService getQos() {
		return qos;
	}

	public void setQos(QualityOfService qos) {
		this.qos = qos;
	}

	public TimeTagType getType() {
		return type;
	}

	public void setType(TimeTagType type) {
		this.type = type;
	}

	@Override
	public String toString() {
		return "[time=" + time + ", frameSequenceNumber=" + frameSequenceNumber + ", qos=" + qos + ", type=" + type + "]";
	}

}
