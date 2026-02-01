package com.leosatdata.model;

public enum QueryResponse {

	ACK, NACK, UNKNOWN;

	public static QueryResponse valueOfCode(int code) {
		if (code == 0) {
			return ACK;
		} else if (code == 1) {
			return NACK;
		}
		return UNKNOWN;
	}

}
