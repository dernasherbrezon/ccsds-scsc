package com.leosatdata.ccsds7321b3.model;

// 4.1.4.2.2.2 Itemized Construction Rules
public enum ConstructionRules {

	// @formatter:off
//  Packets Spanning Multiple Frames. TFDZ Construction Rule ‘000’ shall
//	indicate a fixed-length TFDZ whose contents are CCSDS Packets that are concatenated
//	together and span Transfer Frame boundaries. The First Header Pointer (FHP) is required for
//	packet extraction.
	PSMF(0b000),
	START_VCDU(0b001),
	CONTINUING_VCDU(0b010),
	OCTET_STREAM(0b011),
	STARTING_SEGMENT(0b100),
	CONTINUING_SEGMENT(0b101),
	LAST_SEGMENT(0b110),
	NO_SEGMENTATION(0b111),
	UNKNOWN(255);
	// @formatter:on

	private final int code;

	private ConstructionRules(int code) {
		this.code = code;
	}

	public int getCode() {
		return code;
	}

	public static ConstructionRules valueOfCode(int code) {
		for (ConstructionRules cur : values()) {
			if (cur.code == code) {
				return cur;
			}
		}
		return UNKNOWN;
	}

}
