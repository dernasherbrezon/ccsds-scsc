package com.leosatdata.util;

import java.io.Closeable;
import java.io.IOException;
import java.io.OutputStream;

public class BitOutputStream implements Closeable {

	private final OutputStream os;
	private byte writeBuffer[] = new byte[8];
	private int spareBitsInCurrentByte = 8;
	private int current;

	public BitOutputStream(OutputStream os) {
		this.os = os;
	}

	public void writeUnsignedLong(int numberOfBits, long v) throws IOException {
		if (numberOfBits > BitInputStream.MAX_UNSIGNED_LONG) {
			throw new IllegalArgumentException("unsigned long max length is 63");
		}
		int remaining = numberOfBits;
		while (remaining > 0) {
			if (spareBitsInCurrentByte == 0) {
				os.write(current);
				spareBitsInCurrentByte = 8;
				current = 0;
			}
			if (remaining >= spareBitsInCurrentByte) {
				int shift = remaining - spareBitsInCurrentByte;
				current |= (v >>> shift) & ((1 << spareBitsInCurrentByte) - 1);
				remaining -= spareBitsInCurrentByte;
				spareBitsInCurrentByte = 0;
			} else {
				int shift = spareBitsInCurrentByte - remaining;
				long value = (v) & ((1 << remaining) - 1);
				current |= value << shift;
				remaining = 0;
				spareBitsInCurrentByte = shift;
			}
		}
		if (spareBitsInCurrentByte == 0) {
			os.write(current);
			spareBitsInCurrentByte = 8;
			current = 0;
		}
	}

	public void writeUnsignedInt(int numberOfBits, int value) throws IOException {
		if (numberOfBits > 31) {
			throw new IllegalArgumentException("unsigned int max length is 31");
		}
		writeUnsignedLong(numberOfBits, value);
	}

	public void writeBoolean(boolean b) throws IOException {
		writeUnsignedInt(1, b ? 1 : 0);
	}

	public void writeUnsignedByte(int code) throws IOException {
		writeUnsignedInt(8, code);
	}

	public void writeLong(long v) throws IOException {
		writeBuffer[0] = (byte) (v >>> 56);
		writeBuffer[1] = (byte) (v >>> 48);
		writeBuffer[2] = (byte) (v >>> 40);
		writeBuffer[3] = (byte) (v >>> 32);
		writeBuffer[4] = (byte) (v >>> 24);
		writeBuffer[5] = (byte) (v >>> 16);
		writeBuffer[6] = (byte) (v >>> 8);
		writeBuffer[7] = (byte) (v >>> 0);
		os.write(writeBuffer, 0, 8);
	}

	public void writeFloat(float v) throws IOException {
		int bits = Float.floatToIntBits(v);
		os.write((bits >>> 24) & 0xFF);
		os.write((bits >>> 16) & 0xFF);
		os.write((bits >>> 8) & 0xFF);
		os.write((bits >>> 0) & 0xFF);
	}

	public void writeFully(byte[] payloadData) throws IOException {
		os.write(payloadData);
	}

	@Override
	public void close() throws IOException {
		os.close();
	}

}
