package com.leosatdata.model;

public enum CarrierModulation {

	NO_MODULATION(0b00), 
	PSK(0b01), // Used is PM (phase modulation) in CCSDS 211.1
	FSK(0b10),
	QPSK(0b11),
	UNKNOWN(255);

	private final int code;

	private CarrierModulation(int code) {
		this.code = code;
	}

	public int getCode() {
		return code;
	}

	public static CarrierModulation valueOfCode(int code) {
		for (CarrierModulation cur : values()) {
			if (cur.code == code) {
				return cur;
			}
		}
		return UNKNOWN;
	}

}
