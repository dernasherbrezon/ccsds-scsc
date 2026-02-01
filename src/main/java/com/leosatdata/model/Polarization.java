package com.leosatdata.model;

public enum Polarization {

	LHCP, RHCP, UNKNOWN;

	public static Polarization valueOfCode(int code) {
		if (code == 0) {
			return LHCP;
		} else if (code == 1) {
			return RHCP;
		}
		return UNKNOWN;
	}

}
