package com.leosatdata.service;

import static org.junit.Assert.assertArrayEquals;

import java.util.Properties;

import org.junit.Before;
import org.junit.Test;

import com.leosatdata.util.Configuration;
import com.leosatdata.util.Crc32c;
import com.leosatdata.util.Util;

public class TcpCsSublayerTest {

	private static final String ASM = "FAF320";

	private MockRxCallback rx;
	private TcpCsSublayer cs;

	@Test
	public void testSuccess() throws Exception {
		handle("01");
		assertArrayEquals(Util.hexToBytes("FAF32001A016D052"), rx.getFrames().get(0));
	}

	@Before
	public void start() {
		Properties props = new Properties();
		props.put("cs.sublayer.asm", "FAF320");
		props.put("maximum_frame_length", "100");
		props.put("cs.sublayer.asm.prepend", "true");
		props.put("cs.sublayer.crc.append", "true");
		Configuration config = new Configuration(props);
		rx = new MockRxCallback();
		cs = new TcpCsSublayer(config, rx);
	}

	private void handle(String array) {
		byte[] bytes = createFrame(array);
		cs.handle(bytes, 0, bytes.length);
	}

	private static byte[] createFrame(String array) {
		byte[] asm = Util.hexToBytes(ASM);
		byte[] bytes = Util.hexToBytes(array);
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

}
