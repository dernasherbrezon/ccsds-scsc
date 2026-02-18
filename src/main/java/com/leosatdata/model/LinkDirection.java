package com.leosatdata.model;

public enum LinkDirection {

	RETURN, FORWARD, UNKNOWN;

	public static LinkDirection valueOfCode(int code) {
		if (code == 0) {
			return RETURN;
		} else if (code == 1) {
			return FORWARD;
		}
		return UNKNOWN;
	}

	public int getCode() {
		if (this == RETURN) {
			return 0;
		} else {
			return 1;
		}
	}

}
