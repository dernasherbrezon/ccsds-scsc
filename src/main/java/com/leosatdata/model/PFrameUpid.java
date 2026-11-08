package com.leosatdata.model;

public enum PFrameUpid {

	COPP_CONTROL_COMMAND(0b00010), PROXIMITY_1_SPDU(0b00111);

	private final int code;

	private PFrameUpid(int code) {
		this.code = code;
	}

	public int getCode() {
		return code;
	}

}
