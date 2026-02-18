package com.leosatdata.model;

public enum DataModulation {

	NRZ_L, MANCHESTER, RESERVED, UNKNOWN;

	public static DataModulation valueOfCode(int code) {
		switch (code) {
		case 0b00:
			return NRZ_L;
		case 0b01:
			return MANCHESTER;
		case 0b10:
		case 0b11:
			return RESERVED;
		default:
			return UNKNOWN;
		}
	}

	public int getCode() {
		switch (this) {
		case NRZ_L:
			return 0b00;
		case MANCHESTER:
			return 0b01;
		default:
			return 0b11;
		}
	}

}
