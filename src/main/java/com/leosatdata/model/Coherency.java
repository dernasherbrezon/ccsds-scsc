package com.leosatdata.model;

// B4.5 RECEIVER MODULATION
// E2.2.10 Coherent/Non-coherent
public enum Coherency {

	COHERENT_PSK, NON_COHERENT_PSK, UNKNOWN;

	public static Coherency valueOfCode(int code) {
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
