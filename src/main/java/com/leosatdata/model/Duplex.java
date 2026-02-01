package com.leosatdata.model;

public enum Duplex {

	NO_CHANGE(0b000), FULL_DUPLEX(0b001), HALF_DUPLEX(0b010), SIMPLEX_TRANSMIT(0b011), SIMPLEX_RECEIVE(0b100), RESERVED(255);

	private final int code;

	private Duplex(int code) {
		this.code = code;
	}

	public int getCode() {
		return code;
	}

	public static Duplex valueOfCode(int code) {
		for (Duplex cur : values()) {
			if (cur.code == code) {
				return cur;
			}
		}
		return RESERVED;
	}

}
