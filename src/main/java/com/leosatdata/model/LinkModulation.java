package com.leosatdata.model;

public enum LinkModulation {

	PCM, GMSK, UNKNOWN;

	public static LinkModulation valueOfCode(int code) {
		if (code == 0b0000) {
			return PCM;
		}
		if (code == 0b0001) {
			return GMSK;
		}
		return UNKNOWN;
	}

}
