/*
 * Copyright 2026 Andrey Rodionov
 * SPDX-License-Identifier: Apache-2.0
 */
package com.leosatdata.service;

import static org.junit.Assert.assertArrayEquals;

import java.util.Arrays;
import java.util.Collection;
import java.util.Properties;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import com.leosatdata.util.Configuration;

@RunWith(Parameterized.class)
public class CsSublayerTest {

	private String asm;
	private boolean prependAsm;
	private boolean appendCrc;

	public CsSublayerTest(String asm, boolean prependAsm, boolean appendCrc) {
		this.asm = asm;
		this.prependAsm = prependAsm;
		this.appendCrc = appendCrc;
	}

	@Test
	public void testEncodeDecode() {
		Properties props = new Properties();
		props.setProperty("cs.sublayer.asm", asm);
		props.setProperty("cs.sublayer.asm.prepend", String.valueOf(prependAsm));
		props.setProperty("cs.sublayer.crc.append", String.valueOf(appendCrc));
		Configuration config = new Configuration(props);
		CsSublayer cs = new CsSublayer(config);
		byte[] input = new byte[] { 1, 2, 3 };
		assertArrayEquals(input, cs.decode(cs.encode(input)));
	}

	// @formatter:off
	@Parameterized.Parameters
	public static Collection<?> parameters() {
		return Arrays.asList(new Object[][] { 
			{ "FAF320", false, false }, 
			{ "FAF320", false, true }, 
			{ "FAF320", true, false }, 
			{ "FAF320", true, true }
		});
	}
	// @formatter:on
}
