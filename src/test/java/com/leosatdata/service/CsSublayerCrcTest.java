/*
 * Copyright 2026 Andrey Rodionov
 * SPDX-License-Identifier: Apache-2.0
 */
package com.leosatdata.service;

import static org.junit.Assert.assertNull;

import java.io.ByteArrayOutputStream;
import java.util.Properties;

import org.junit.Test;

import com.leosatdata.ccsds2110b6.util.TransferFrameParser;
import com.leosatdata.util.BitOutputStream;
import com.leosatdata.util.Configuration;

public class CsSublayerCrcTest {

	@Test
	public void testInvalidData() throws Exception {
		Properties props = new Properties();
		props.setProperty("cs.sublayer.asm", "FAF320");
		props.setProperty("cs.sublayer.asm.prepend", String.valueOf(true));
		props.setProperty("cs.sublayer.crc.append", String.valueOf(true));
		props.setProperty("maximum_frame_length", "255"); // some arbitary big number
		Configuration config = new Configuration(props);
		CsSublayer cs = new CsSublayer(config);

		ByteArrayOutputStream baos = new ByteArrayOutputStream();
		try (BitOutputStream bos = new BitOutputStream(baos)) {
			TransferFrameParser.encode(CsSublayerTest.createFrame(), bos);
		}

		byte[] input = baos.toByteArray();

		byte[] encoded = cs.encode(input);
		encoded[7]++;
		assertNull(cs.decode(encoded)); // invalid crc
		encoded[0]++;
		assertNull(cs.decode(encoded)); // invalid asm
		assertNull(cs.decode(new byte[2])); // too small for asm
		assertNull(cs.decode(new byte[] { (byte) 0xfa, (byte) 0xf3, (byte) 0x20, 1 })); // too small for crc
	}

}
