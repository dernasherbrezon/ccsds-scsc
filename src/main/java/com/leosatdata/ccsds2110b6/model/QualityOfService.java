package com.leosatdata.ccsds2110b6.model;

public enum QualityOfService {

	SEQUENCE_CONTROLLED(0b0), EXPEDITED(0b1), UNKNOWN(255);

	private final int code;

	private QualityOfService(int code) {
		this.code = code;
	}

	public int getCode() {
		return code;
	}

	public static QualityOfService valueOfCode(int code) {
		for (QualityOfService cur : values()) {
			if (cur.code == code) {
				return cur;
			}
		}
		return UNKNOWN;
	}

}
