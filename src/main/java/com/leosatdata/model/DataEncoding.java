package com.leosatdata.model;

public enum DataEncoding {

	LDPC(0b00), CONVOLUTIONAL(0b01), NONE(0b10), CONCANTENATED(0b11), UNKNOWN(255);

	private final int code;

	private DataEncoding(int code) {
		this.code = code;
	}

	public int getCode() {
		return code;
	}

	public static DataEncoding valueOfCode(int code) {
		for (DataEncoding cur : values()) {
			if (cur.code == code) {
				return cur;
			}
		}
		return UNKNOWN;
	}

}
