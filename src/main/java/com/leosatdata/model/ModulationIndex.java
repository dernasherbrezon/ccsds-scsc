/*
 * Copyright 2026 Andrey Rodionov
 * SPDX-License-Identifier: Apache-2.0
 */
package com.leosatdata.model;

public enum ModulationIndex {

	// all modulations in rad/pk format
	M0(0, 0b000), M0_4(0.4f, 0b001), M0_6(0.6f, 0b010), M0_8(0.8f, 0b011), 
	MPI_3((float) (Math.PI / 3), 0b100), M1_15(1.15f, 0b101), M1_3(1.3f, 0b110), M1_4(1.4f, 0b111),
	MPI_2((float) (Math.PI / 2), 254), // used only by Type1 & Transceiver
	UNKNOWN(0.0f, 255);

	private final float mod;
	private final int code;

	private ModulationIndex(float mod, int code) {
		this.mod = mod;
		this.code = code;
	}

	public float getMod() {
		return mod;
	}

	public int getCode() {
		return code;
	}

	public static ModulationIndex valueOfCode(int code) {
		for (ModulationIndex cur : values()) {
			if (cur.code == code) {
				return cur;
			}
		}
		return UNKNOWN;
	}

}
