package com.leosatdata.ccsds2110B6.model;

public enum PduTypeId {

	USER_DATA(0b0), SPDU(0b1), UNKNOWN(255);

	private final int code;

	private PduTypeId(int code) {
		this.code = code;
	}

	public int getCode() {
		return code;
	}

	public static PduTypeId valueOfCode(int code) {
		for (PduTypeId cur : values()) {
			if (cur.code == code) {
				return cur;
			}
		}
		return UNKNOWN;
	}

}
