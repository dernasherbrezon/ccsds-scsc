package com.leosatdata.model;

public enum Mode {

	MISSION_SPECIFIC, PROXIMITY1, RESERVED, UNKNOWN;

	public static Mode valueOfCode(int code) {
		switch (code) {
		case 0b000:
		case 0b010:
		case 0b011:
		case 0b100:
			return MISSION_SPECIFIC;
		case 0b001:
			return PROXIMITY1;
		case 0b110:
		case 0b111:
			return RESERVED;
		default:
			return UNKNOWN;
		}
	}

}
