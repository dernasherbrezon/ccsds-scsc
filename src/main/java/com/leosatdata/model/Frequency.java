package com.leosatdata.model;

public enum Frequency {

	CH0R(0b000), CH1R(0b001), CH2R(0b010), CH3R(0b011), CH4R(0b100), CH5R(0b101), CH6R(0b110), CH7R(0b111), UNKNOWN(255); 

	private final int code;

	private Frequency(int code) {
		this.code = code;
	}

	public int getCode() {
		return code;
	}

	public static Frequency valueOfCode(int code) {
		for (Frequency cur : values()) {
			if (cur.code == code) {
				return cur;
			}
		}
		return UNKNOWN;
	}

}
