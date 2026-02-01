package com.leosatdata.model;

public enum Coding {

	UNCODED(0b000000), LDPC2048_1024(0b000001), LDPC6144_4096(0b000101), LDPC8160_7136(0b001010), UNKNOWN(255);

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
