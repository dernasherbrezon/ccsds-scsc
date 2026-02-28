package com.leosatdata.service;

import java.io.InputStream;
import java.io.OutputStream;

import com.leosatdata.util.Configuration;

public class Node {

	private final NullTransceiver transceiver;
	private final FrameSublayer frameSublayer;
	private final Mac mac;
	private final InputStream is;
	private final OutputStream os;

	public Node(Configuration config, NodeCallback callback) {
		transceiver = new NullTransceiver(config);
		frameSublayer = new FrameSublayer(config, transceiver, callback);
		mac = new Mac(config, transceiver, frameSublayer, callback);
		is = new FrameInputStream();
		frameSublayer.setUserDataCallback((FrameInputStream) is);
		os = new FrameOutputStream(frameSublayer);
	}

	public void start() {
		transceiver.start();
		frameSublayer.start();
		mac.start();
	}

	public void stop() {
		mac.stop();
		frameSublayer.stop();
		transceiver.stop();
	}

	// used only in tests to link 2 transceivers
	public NullTransceiver getTransceiver() {
		return transceiver;
	}

	public InputStream getInputStream() {
		return is;
	}

	public OutputStream getOutputStream() {
		return os;
	}

}
