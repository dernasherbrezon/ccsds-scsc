package com.leosatdata.model;

public class TimeDistribution {

	private TimeDistributionType type;
	// FIXME see reference 3. 5bytes coarse, 3bytes fine
	private long transceiverClock; // clock corresponding to when the trailing edge of the last bit of the ASM of
	// the transmitted PLTU crosses the clock capture point within the transceiver;
	// FIXME 1 coarse, 2 bytes fine
	private int sendSideDelay;
	// FIXME 1 coarse, 2 bytes fine
	private int oneWayLightTime;

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

}
