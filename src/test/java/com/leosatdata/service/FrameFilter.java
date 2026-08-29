/*
 * Copyright 2026 Andrey Rodionov
 * SPDX-License-Identifier: Apache-2.0
 */
package com.leosatdata.service;

public interface FrameFilter {

	boolean accept(NullTransceiver tx, NullTransceiver rx, byte[] data);
	
}
