package com.leosatdata.model;

public enum NegotiationType {

	DEMAND, QUERY, UNKNOWN;

	public static NegotiationType valueOfCode(int code) {
		if (code == 0) {
			return DEMAND;
		} else if (code == 1) {
			return QUERY;
		}
		return UNKNOWN;
	}

	public int getCode() {
		if (this == DEMAND) {
			return 0;
		} else {
			return 1;
		}
	}

}
