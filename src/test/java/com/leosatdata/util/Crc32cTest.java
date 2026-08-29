/*
 * Copyright 2026 Andrey Rodionov
 * SPDX-License-Identifier: Apache-2.0
 */
package com.leosatdata.util;

import static org.junit.Assert.assertEquals;

import java.nio.charset.StandardCharsets;

import org.junit.Test;

public class Crc32cTest {
	
	@Test
	public void testSuccess() {
		byte[] data = "123456789".getBytes(StandardCharsets.ISO_8859_1);
		long result = Crc32c.calculate(data);
		assertEquals(0xE3069283L, result);
	}

}
