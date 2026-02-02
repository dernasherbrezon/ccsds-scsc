package com.leosatdata.model;

public enum RangingReportRequest {

	NOT_REQUIRED(0b00000), CONFIGURATION(0b00001), DELAY_CALIBRATION(0b00010), POSITION_VELOCITY_TIME(0b00011), OBSERVABLE(0b00100), UNKNOWN(255);

	private final int code;

	private RangingReportRequest(int code) {
		this.code = code;
	}

	public int getCode() {
		return code;
	}

	public static RangingReportRequest valueOfCode(int code) {
		for (RangingReportRequest cur : values()) {
			if (cur.code == code) {
				return cur;
			}
		}
		return UNKNOWN;
	}

}
