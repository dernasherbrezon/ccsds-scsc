package com.leosatdata.model;

public enum Type5NegotiationType {

	DEMAND(0b000), QUERY_REQ(0b001), QUERY_ACK(0b010), QUERY_NACK(0b011), REPORT(0b100), REQUEST(0b101), UNKNOWN(255);

	private final int code;

	private Type5NegotiationType(int code) {
		this.code = code;
	}

	public static Type5NegotiationType valueOfCode(int code) {
		for (Type5NegotiationType cur : values()) {
			if (cur.code == code) {
				return cur;
			}
		}
		return UNKNOWN;
	}
	
	public int getCode() {
		return code;
	}

}
