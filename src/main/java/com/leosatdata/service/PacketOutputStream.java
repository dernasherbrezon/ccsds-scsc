package com.leosatdata.service;

import java.io.IOException;

import com.leosatdata.ccsds2110b6.model.QualityOfService;
import com.leosatdata.util.Configuration;

public class PacketOutputStream {

	private final Node node;
	private final FrameSublayer frameSublayer;
	private final QualityOfService qos;
	private final int vcid;
	private final boolean fixedFrameLength;
	private final int maxDataLength;
	// buffer to keep outgoing messages
	private final byte[] buffer;
	// The number of valid bytes in the buffer
	private int count;

	private Object currentFrame;

	public PacketOutputStream(Node node, FrameSublayer frameSublayer, Configuration config, QualityOfService qos, int vcid) {
		this.node = node;
		this.frameSublayer = frameSublayer;
		this.qos = qos;
		this.vcid = vcid;
		this.maxDataLength = frameSublayer.getMaxDataLength();
		int frameVersion = config.getInteger("u_frame_version", 3);
		if (frameVersion == 2) {
			// v2 is always fixed
			this.fixedFrameLength = true;
		} else if (frameVersion == 3) {
			// v3 is always variable
			this.fixedFrameLength = false;
		} else if (frameVersion == 4) {
			this.fixedFrameLength = config.getBoolean("framev4.fixedLength");
		} else {
			throw new IllegalArgumentException("unsupported frame version: " + frameVersion);
		}
		this.buffer = new byte[this.maxDataLength];
	}

	public void write(byte b[], int off, int len) throws IOException {
		int remaining = maxDataLength - count;
		if (len > remaining) {
			System.arraycopy(b, off, buffer, count, remaining);

		}
	}

	private void flushBuffer() throws IOException {

	}

}
