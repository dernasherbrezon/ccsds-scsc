/*
 * Copyright 2026 Andrey Rodionov
 * SPDX-License-Identifier: Apache-2.0
 */
package com.leosatdata.model;

public enum StatusType {

	LOCAL_SCID(0b0000), MINIMUM_SURFACE_ELEVATION_ANGLE(0b0001), ALERT_MESSAGE(0b0010), UNKNOWN(255);

	private final int code;

	private StatusType(int code) {
		this.code = code;
	}

	public static StatusType valueOfCode(int code) {
		for (StatusType cur : values()) {
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
