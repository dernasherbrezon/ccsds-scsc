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

	public int getCode() {
		switch (this) {
		case MISSION_SPECIFIC:
			return 0b100;
		case PROXIMITY1:
			return 0b001;
		case RESERVED:
			return 0b111;
		default:
			return 0b111;
		}
	}

}
