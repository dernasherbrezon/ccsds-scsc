package com.leosatdata.service;

import java.io.IOException;
import java.io.OutputStream;

public class FrameOutputStream extends OutputStream {

	private final FrameSublayer frameSublayer;

	public FrameOutputStream(FrameSublayer frameSublayer) {
		this.frameSublayer = frameSublayer;
	}

	@Override
	public void write(int b) throws IOException {
		// TODO Auto-generated method stub
		// FIXME call frameSublayer.sendUserFrame(object);
	}

}
