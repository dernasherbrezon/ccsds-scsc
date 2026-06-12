package com.leosatdata.model;

public enum Type4CarrierModulation {

	PCM_PM_BI_PHASE(0b0000), GMSK(0b0001), OQPSK(0b0010), BPSK(0b0011), PCM_PSK_PM(0b0100), PCM_PM_NRZL(0b0101), UNKNOWN(255);

	private final int code;

	private Type4CarrierModulation(int code) {
		this.code = code;
	}

	public static Type4CarrierModulation valueOfCode(int code) {
		for (Type4CarrierModulation cur : values()) {
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
