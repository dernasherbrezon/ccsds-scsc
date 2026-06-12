package com.leosatdata.model;

public enum Type1CarrierModulation {

	NO_MODULATION(0b00), 
	PCM_PSK_PM(0b01), // Used as PM (phase modulation) in CCSDS 211.1
	FSK(0b10),
	QPSK(0b11),
	UNKNOWN(255);

	private final int code;

	private Type1CarrierModulation(int code) {
		this.code = code;
	}

	public int getCode() {
		return code;
	}

	public static Type1CarrierModulation valueOfCode(int code) {
		for (Type1CarrierModulation cur : values()) {
			if (cur.code == code) {
				return cur;
			}
		}
		return UNKNOWN;
	}

}
