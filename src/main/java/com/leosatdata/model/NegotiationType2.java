package com.leosatdata.model;

public enum NegotiationType2 {

	DEMAND(0b000), QUERY_REQ(0b001), QUERY_ACK(0b010), QUERY_NACK(0b011), REPORT(0b100), REQUEST(0b101), UNKNOWN(255);

	private final int code;

	private NegotiationType2(int code) {
		this.code = code;
	}

	public static NegotiationType2 valueOfCode(int code) {
		for (NegotiationType2 cur : values()) {
			if (cur.code == code) {
				return cur;
			}
		}
		return UNKNOWN;
	}

}
