/*
 * Copyright 2026 Andrey Rodionov
 * SPDX-License-Identifier: Apache-2.0
 */
package com.leosatdata.service;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertTrue;

import java.util.Properties;

import org.junit.Before;
import org.junit.Test;

import com.leosatdata.util.Configuration;
import com.leosatdata.util.Crc32c;
import com.leosatdata.util.Util;

public class TcpCsSublayerTest {

	private static final String ASM = "FAF320";

	private MockTransceiverCallback rx;
	private TcpCsSublayer cs;

	@Test
	public void testSuccess() throws Exception {
		// small payload
		handle("01");
		assertArrayEquals(Util.hexToBytes("FAF32001A016D052"), rx.getFrames().get(0));

		// bigger payload
		rx.getFrames().clear();
		handle("01020304");
		assertArrayEquals(Util.hexToBytes("FAF3200102030429308CF4"), rx.getFrames().get(0));

		// payload with just asm in it
		rx.getFrames().clear();
		handle("FAF320");
		assertArrayEquals(Util.hexToBytes("FAF320FAF3209A3EB68F"), rx.getFrames().get(0));

		// payload with asm and some extra data
		rx.getFrames().clear();
		handle("01FAF320");
		assertArrayEquals(Util.hexToBytes("FAF32001FAF3206F78F48A"), rx.getFrames().get(0));

		// 2 frames in the same update
		rx.getFrames().clear();
		handle(concat(createPdu("0102"), createPdu("0909")));
		assertArrayEquals(Util.hexToBytes("FAF320010203F89F52"), rx.getFrames().get(0));
		assertArrayEquals(Util.hexToBytes("FAF32009090765F7D1"), rx.getFrames().get(1));

		// partial frame
		rx.getFrames().clear();
		byte[] frame = createPdu("0102");
		handle(cut(frame, 0, 4));
		assertTrue(rx.getFrames().isEmpty());
		handle(cut(frame, 4, frame.length));
		assertArrayEquals(Util.hexToBytes("FAF320010203F89F52"), rx.getFrames().get(0));

		// partial frame with enough data for crc check
		rx.getFrames().clear();
		frame = createPdu("0102");
		int length = 3 + 4 + 1;
		handle(cut(frame, 0, length));
		assertTrue(rx.getFrames().isEmpty());
		handle(cut(frame, length, frame.length));
		assertArrayEquals(Util.hexToBytes("FAF320010203F89F52"), rx.getFrames().get(0));

		// idle data is not supported
		rx.getFrames().clear();
		handle(concat(concat(createPdu("0102"), new byte[] { 01, 02 }), createPdu("0909")));
		assertArrayEquals(Util.hexToBytes("FAF32009090765F7D1"), rx.getFrames().get(0));

		// write beyound max frame length
		rx.getFrames().clear();
		handle(concat(concat(createPdu("0102"), createPdu("0909")), createPdu("AAAA")));
		assertArrayEquals(Util.hexToBytes("FAF320010203F89F52"), rx.getFrames().get(0));
		assertArrayEquals(Util.hexToBytes("FAF32009090765F7D1"), rx.getFrames().get(1));
		assertArrayEquals(Util.hexToBytes("FAF320AAAA06D100E1"), rx.getFrames().get(2));

	}

	@Before
	public void start() {
		Properties props = new Properties();
		props.put("cs.sublayer.asm", "FAF320");
		props.put("maximum_frame_length", "20");
		props.put("cs.sublayer.asm.prepend", "true");
		props.put("cs.sublayer.crc.append", "true");
		Configuration config = new Configuration(props);
		rx = new MockTransceiverCallback();
		cs = new TcpCsSublayer(config, rx);
	}

	private void handle(String frame) {
		handle(createPdu(frame));
	}

	private void handle(byte[] pdu) {
		cs.handle(pdu, 0, pdu.length);
	}

	private static byte[] createPdu(String frame) {
		byte[] asm = Util.hexToBytes(ASM);
		byte[] bytes = Util.hexToBytes(frame);
		long crc = Crc32c.calculate(bytes);
		byte[] result = new byte[asm.length + bytes.length + 4];
		System.arraycopy(asm, 0, result, 0, asm.length);
		System.arraycopy(bytes, 0, result, asm.length, bytes.length);
		result[result.length - 4] = (byte) ((crc >>> 24) & 0xFF);
		result[result.length - 3] = (byte) ((crc >>> 16) & 0xFF);
		result[result.length - 2] = (byte) ((crc >>> 8) & 0xFF);
		result[result.length - 1] = (byte) ((crc >>> 0) & 0xFF);
		return result;
	}

	private static byte[] concat(byte[] a1, byte[] a2) {
		byte[] result = new byte[a1.length + a2.length];
		System.arraycopy(a1, 0, result, 0, a1.length);
		System.arraycopy(a2, 0, result, a1.length, a2.length);
		return result;
	}

	private static byte[] cut(byte[] array, int from, int to) {
		byte[] result = new byte[to - from];
		System.arraycopy(array, from, result, 0, result.length);
		return result;
	}
}
