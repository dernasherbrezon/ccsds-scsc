/*
 * Copyright 2026 Andrey Rodionov
 * SPDX-License-Identifier: Apache-2.0
 */
package com.leosatdata.service;

public interface SpduCallback {

	void onSpdu(byte[] spdu);
	
	void onSpduTx();

}
