package com.leosatdata.model;

public enum Direction {

	TRANSMITTER, RECEIVER, UNKNOWN;

	public static Direction valueOfCode(int code) {
		if (code == 0) {
			return TRANSMITTER;
		} else if (code == 1) {
			return RECEIVER;
		}
		return UNKNOWN;
	}

	public int getCode() {
		if (this == TRANSMITTER) {
			return 0;
		} else {
			return 1;
		}
	}

}
