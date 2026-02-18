package com.leosatdata.model;

public enum TimeDistributionType {

	NULL(0b00000000), TIME_TRANSFER(0b00000001), RESERVED(255);

	private final int code;

	private TimeDistributionType(int code) {
		this.code = code;
	}

	public static TimeDistributionType valueOfCode(int code) {
		for (TimeDistributionType cur : values()) {
			if (cur.code == code) {
				return cur;
			}
		}
		return RESERVED;
	}

	public int getCode() {
		return code;
	}

}
