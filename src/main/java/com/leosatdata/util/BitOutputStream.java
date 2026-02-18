package com.leosatdata.util;

import java.io.Closeable;
import java.io.IOException;
import java.io.OutputStream;

public class BitOutputStream implements Closeable {

	private final OutputStream os;

	public BitOutputStream(OutputStream os) {
		this.os = os;
	}

	public void writeUnsignedInt(int numberOfBits, int value) throws IOException {
		// TODO Auto-generated method stub

	}

	public void writeBoolean(boolean b) throws IOException {
		// TODO Auto-generated method stub

	}

	public void writeFully(byte[] payloadData) throws IOException {
		// TODO Auto-generated method stub

	}

	@Override
	public void close() throws IOException {
		os.close();
	}

}
