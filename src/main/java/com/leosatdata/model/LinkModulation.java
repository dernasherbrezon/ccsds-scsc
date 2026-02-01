package com.leosatdata.model;

public enum LinkModulation {

	PCM_PM_BI_PHASE(0b0000), GMSK(0b0001), OQPSK(0b0010), BPSK(0b0011), PCM_PSK_PM(0b0100), PCM_PM_NRZL(0b0101), UNKNOWN(255);

	private final int code;

	private LinkModulation(int code) {
		this.code = code;
	}

	public static LinkModulation valueOfCode(int code) {
		for (LinkModulation cur : values()) {
			if (cur.code == code) {
				return cur;
			}
		}
		return UNKNOWN;
	}

}
