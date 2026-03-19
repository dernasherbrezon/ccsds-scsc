package com.leosatdata.service;

import java.io.IOException;
import java.io.OutputStream;

import com.leosatdata.ccsds2110b6.model.QualityOfService;
import com.leosatdata.util.Configuration;

public class FrameOutputStream extends OutputStream {

	private final Node node;
	private final QualityOfService qos;
	private final int vcid;
	private final boolean fixedFrameLength;
	private final int dataLength;
	// buffer to keep outgoing messages
	private final byte[] buffer;
	// The number of valid bytes in the buffer
	private int count;

	public FrameOutputStream(Node node, Configuration config, QualityOfService qos, int vcid) {
		this.node = node;
		this.qos = qos;
		this.vcid = vcid;
		int frameVersion = config.getInteger("u_frame_version", 3);
		// FIXME populate for other frame types
		if (frameVersion == 2) {
			this.fixedFrameLength = false;
			this.dataLength = 0;
		} else if (frameVersion == 3) {
			this.fixedFrameLength = false;
			this.dataLength = 0;
		} else if (frameVersion == 4) {
			this.fixedFrameLength = config.getBoolean("framev4.fixedLength");
			int fecLength = config.getBoolean("framev4.frameErrorControl") ? 2 : 0;
			int headerLength = 56 / 8 + 1; // maximum virtual channel frame count is 255, thus "Length of Virtual Channel
											// Frame Count Field" is 1 octet. the rest is mandatory header bits
			this.dataLength = config.getInteger("maximum_frame_length", 2043) - config.getInteger("framev4.insertZoneLength", 0) - fecLength - headerLength;
		} else {
			throw new IllegalArgumentException("unsupported frame version: " + frameVersion);
		}
		this.buffer = new byte[this.dataLength];
	}

	@Override
	public void write(int b) throws IOException {
		if (count >= buffer.length) {
			flushBuffer();
		}
		buffer[count++] = (byte) b;
	}

	private void flushBuffer() throws IOException {

	}

}
