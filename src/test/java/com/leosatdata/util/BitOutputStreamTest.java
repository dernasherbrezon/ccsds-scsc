package com.leosatdata.util;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

import org.junit.Test;

public class BitOutputStreamTest {

	@Test
	public void testSuccess() throws IOException {
		byte[] data = new byte[] { (byte) 0b1101_0101, 0b0010_1010, 0b0011_0001, 0b0100_1000 };
		ByteArrayOutputStream baos = new ByteArrayOutputStream();
		BitOutputStream bos = new BitOutputStream(baos);
		bos.writeUnsignedInt(10, 0b1101_0101_00);
		bos.writeBoolean(true);
		bos.writeUnsignedInt(4, 0b0101);
		bos.writeUnsignedInt(17, 0b0_0011_0001_0100_1000);
		bos.close();
		assertArrayEquals(data, baos.toByteArray());
	}

	@Test
	public void testFlush() throws IOException {
		ByteArrayOutputStream baos = new ByteArrayOutputStream();
		BitOutputStream bos = new BitOutputStream(baos);
		bos.writeUnsignedInt(4, 0);
		bos.writeUnsignedInt(4, 1);
		bos.writeFully(new byte[] { 2, 3 });
		bos.close();
		byte[] actual = baos.toByteArray();
		assertEquals(1, actual[0]);
		assertEquals(2, actual[1]);
		assertEquals(3, actual[2]);
	}

}
