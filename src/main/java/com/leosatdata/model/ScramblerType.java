package com.leosatdata.model;

public enum ScramblerType {

	BYPASS, CCITT, IESS, UNKNOWN;

	public static ScramblerType valueOfCode(int code) {
		switch (code) {
		case 0b00:
		case 0b10:
			return BYPASS;
		case 0b01:
			return CCITT;
		case 0b11:
			return IESS;
		default:
			return UNKNOWN;
		}
	}

}
