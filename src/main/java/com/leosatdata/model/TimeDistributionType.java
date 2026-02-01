package com.leosatdata.model;

public enum TimeDistributionType {

	NULL, TIME_TRANSFER, RESERVED;

	public static TimeDistributionType valueOfCode(int code) {
		switch (code) {
		case 0b00000000:
			return NULL;
		case 0b00000001:
			return TIME_TRANSFER;
		default:
			return RESERVED;
		}
	}

}
