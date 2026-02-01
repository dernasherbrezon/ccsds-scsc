package com.leosatdata.model;

public enum ModeSelect {

	SUPPRESSED_CARRIER, RESIDUAL_CARRIER, RESERVED, UNKNOWN;

	public static ModeSelect valueOfCode(int code) {
		switch (code) {
		case 0b00:
			return SUPPRESSED_CARRIER;
		case 0b01:
			return RESIDUAL_CARRIER;
		case 0b10:
		case 0b11:
			return RESERVED;
		default:
			return UNKNOWN;
		}
	}

}
