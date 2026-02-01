package com.leosatdata.model;

public enum Coding {

	// @formatter:off
	UNCODED(0b00000), 
	LDPC2048_1024(0b00001),
	LDPC8192_4096(0b00010),
	LDPC32768_16384(0b00011),
	LDPC6144_4096(0b00101),
	LDPC24576_16384(0b00110),
	LDPC20480_16384(0b01001),
	LDPC8160_7136(0b01010),
	CONVOLUTIONAL(0b01011), // 7,1/2
	UNKNOWN(255);
	// @formatter:on

	private final int code;

	private Coding(int code) {
		this.code = code;
	}

	public int getCode() {
		return code;
	}

	public static Coding valueOfCode(int code) {
		for (Coding cur : values()) {
			if (cur.code == code) {
				return cur;
			}
		}
		return UNKNOWN;
	}

}
