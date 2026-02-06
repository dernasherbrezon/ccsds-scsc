package com.leosatdata.ccsds2110B6.model;

public enum SourceDestination {

	SOURCE(0b0), DESTINATION(0b1), UNKNOWN(255);

	private final int code;

	private SourceDestination(int code) {
		this.code = code;
	}

	public int getCode() {
		return code;
	}

	public static SourceDestination valueOfCode(int code) {
		for (SourceDestination cur : values()) {
			if (cur.code == code) {
				return cur;
			}
		}
		return UNKNOWN;
	}

}
