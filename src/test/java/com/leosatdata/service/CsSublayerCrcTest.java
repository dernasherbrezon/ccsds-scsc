/*
 * Copyright 2026 Andrey Rodionov
 * SPDX-License-Identifier: Apache-2.0
 */
package com.leosatdata.service;

import static org.junit.Assert.assertNull;

import java.util.Properties;

import org.junit.Test;

import com.leosatdata.util.Configuration;

public class CsSublayerCrcTest {

	@Test
	public void testInvalidData() {
		Properties props = new Properties();
		props.setProperty("cs.sublayer.asm", "FAF320");
		props.setProperty("cs.sublayer.asm.prepend", String.valueOf(true));
		props.setProperty("cs.sublayer.crc.append", String.valueOf(true));
		Configuration config = new Configuration(props);
		CsSublayer cs = new CsSublayer(config);
		byte[] input = new byte[] { 1, 2, 3 };
		byte[] encoded = cs.encode(input);
		encoded[4]++;
		assertNull(cs.decode(encoded)); // invalid crc
		encoded[0]++;
		assertNull(cs.decode(encoded)); // invalid asm
		assertNull(cs.decode(new byte[2])); // too small for asm
		assertNull(cs.decode(new byte[] { (byte) 0xfa, (byte) 0xf3, (byte) 0x20, 1 })); // too small for crc
	}

}
