package com.leosatdata.model;

public enum Modulation {

	COHERENT_PSK, NON_COHERENT_PSK, UNKNOWN;

	public static Modulation valueOfCode(int code) {
		if (code == 0) {
			return COHERENT_PSK;
		}
		if (code == 1) {
			return NON_COHERENT_PSK;
		}
		return UNKNOWN;
	}

	public int getCode() {
		switch (this) {
		case COHERENT_PSK:
			return 0;
		case NON_COHERENT_PSK:
			return 1;
		default:
			return 0;
		}
	}

}
