package com.leosatdata.model;

public class TimeDistribution implements Spdu {

	private SpduHeader header;

	private TimeDistributionType type;
	private long transceiverClock; // clock corresponding to when the trailing edge of the last bit of the ASM of
	// the transmitted PLTU crosses the clock capture point within the transceiver;
	private int sendSideDelay;
	private int oneWayLightTime;

	@Override
	public SpduHeader getHeader() {
		return header;
	}

	@Override
	public void setHeader(SpduHeader header) {
		this.header = header;
	}

	public TimeDistributionType getType() {
		return type;
	}

	public void setType(TimeDistributionType type) {
		this.type = type;
	}

	public long getTransceiverClock() {
		return transceiverClock;
	}

	public void setTransceiverClock(long transceiverClock) {
		this.transceiverClock = transceiverClock;
	}

	public int getSendSideDelay() {
		return sendSideDelay;
	}

	public void setSendSideDelay(int sendSideDelay) {
		this.sendSideDelay = sendSideDelay;
	}

	public int getOneWayLightTime() {
		return oneWayLightTime;
	}

	public void setOneWayLightTime(int oneWayLightTime) {
		this.oneWayLightTime = oneWayLightTime;
	}

	@Override
	public String toString() {
		return "TimeDistribution [transceiverClock=" + transceiverClock + ", sendSideDelay=" + sendSideDelay + ", oneWayLightTime=" + oneWayLightTime + "]";
	}

}
