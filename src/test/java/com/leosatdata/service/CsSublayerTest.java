/*
 * Copyright 2026 Andrey Rodionov
 * SPDX-License-Identifier: Apache-2.0
 */
package com.leosatdata.service;

import static org.junit.Assert.assertArrayEquals;

import java.io.ByteArrayOutputStream;
import java.util.Arrays;
import java.util.Collection;
import java.util.Properties;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import com.leosatdata.ccsds2110b6.model.DfcId;
import com.leosatdata.ccsds2110b6.model.PduTypeId;
import com.leosatdata.ccsds2110b6.model.QualityOfService;
import com.leosatdata.ccsds2110b6.model.SourceDestination;
import com.leosatdata.ccsds2110b6.model.TransferFrame;
import com.leosatdata.ccsds2110b6.util.TransferFrameParser;
import com.leosatdata.util.BitOutputStream;
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
	public void testEncodeDecode() throws Exception {
		Properties props = new Properties();
		props.setProperty("cs.sublayer.asm", asm);
		props.setProperty("cs.sublayer.asm.prepend", String.valueOf(prependAsm));
		props.setProperty("cs.sublayer.crc.append", String.valueOf(appendCrc));
		props.setProperty("maximum_frame_length", "255"); // some arbitary big number
		Configuration config = new Configuration(props);
		CsSublayer cs = new CsSublayer(config);

		ByteArrayOutputStream baos = new ByteArrayOutputStream();
		try (BitOutputStream bos = new BitOutputStream(baos)) {
			TransferFrameParser.encode(createFrame(), bos);
		}

		byte[] input = baos.toByteArray();
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

	public static TransferFrame createFrame() {
		byte[] payload = new byte[] { 0, 1, 2 };
		TransferFrame frame = new TransferFrame();
		frame.setDfcId(DfcId.USER);
		frame.setFrameSequenceNumber(12);
		frame.setPcid(1);
		frame.setPduTypeId(PduTypeId.USER_DATA);
		frame.setPortId(1);
		frame.setQualityOfService(QualityOfService.EXPEDITED);
		frame.setScid(2);
		frame.setSourceOrDestination(SourceDestination.SOURCE);
		frame.setDataField(payload);
		return frame;
	}
}
