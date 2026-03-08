package com.leosatdata.service;

import java.io.IOException;
import java.io.OutputStream;

import com.leosatdata.service2.FrameSublayer2;

public class FrameOutputStream extends OutputStream {

	private final FrameSublayer2 frameSublayer;

	public FrameOutputStream(FrameSublayer2 frameSublayer) {
		this.frameSublayer = frameSublayer;
	}

	@Override
	public void write(int b) throws IOException {
		// TODO Auto-generated method stub
		// FIXME call frameSublayer.sendUserFrame(object);
	}

}
