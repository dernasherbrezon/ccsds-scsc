/*
 * Copyright 2026 Andrey Rodionov
 * SPDX-License-Identifier: Apache-2.0
 */
package com.leosatdata.service;

import java.io.IOException;
import java.net.Socket;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TcpDelayedTxTask implements Runnable {

	private static final Logger LOG = LoggerFactory.getLogger(TcpDelayedTxTask.class);

	private Socket socket;
	private TransceiverCallback callback;
	private byte[] data;

	public TcpDelayedTxTask(Socket socket, TransceiverCallback callback, byte[] data) {
		this.socket = socket;
		this.callback = callback;
		this.data = data;
	}

	@Override
	public void run() {
		try {
			socket.getOutputStream().write(data);
		} catch (IOException e) {
			LOG.info("unable to send", e);
		} finally {
			// sender doesn't know if receiver is actually working
			callback.onTx();
		}
	}
}
