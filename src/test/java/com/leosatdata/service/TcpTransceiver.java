/*
 * Copyright 2026 Andrey Rodionov
 * SPDX-License-Identifier: Apache-2.0
 */
package com.leosatdata.service;

import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.leosatdata.model.CommParameters;
import com.leosatdata.util.Configuration;
import com.leosatdata.util.NamingThreadFactory;

public class TcpTransceiver implements Transceiver {

	private final static Logger LOG = LoggerFactory.getLogger(TcpTransceiver.class);
	private final Configuration config;
	private final byte[] buffer;

	private TransceiverTxState txState = TransceiverTxState.OFF;
	private ServerSocket server;
	private Socket clientSocket;
	private ScheduledExecutorService executor;
	private Future<?> rxFuture;
	private TransceiverCallback callback;

	private CommParameters tx;
	private Long txDelay;

	public TcpTransceiver(Configuration config) {
		this.config = config;
		this.buffer = new byte[config.getInteger("maximum_frame_length", 1024)];
	}

	public void start() throws Exception {
		executor = Executors.newScheduledThreadPool(2, new NamingThreadFactory("tcp-transceiver-" + config.getInteger("local_spacecraft_id", 0)));
	}

	public void stop() {
		if (executor == null) {
			return;
		}
		if (rxFuture != null) {
			rxFuture.cancel(true);
		}
		if (server != null) {
			closeQuietly(server);
		}
		executor.shutdownNow();
	}

	@Override
	public int send(byte[] data) {
		if (clientSocket == null) {
			LOG.error("can't send data when client disconnected");
			return 0;
		}
		assert (tx != null && tx.getSymbolRate() != 0);
		long txMillisToWait;
		if (txDelay == null) {
			txMillisToWait = (long) ((data.length * 8 * 1000) / tx.getSymbolRate());
		} else {
			txMillisToWait = txDelay;
		}
		// tx should be called by interrupt, i.e. async
		if (txMillisToWait == 0) {
			executor.execute(new TcpDelayedTxTask(clientSocket, callback, data));
		} else {
			executor.schedule(new TcpDelayedTxTask(clientSocket, callback, data), txMillisToWait, TimeUnit.MILLISECONDS);
		}
		return 0;
	}

	@Override
	public int setRxCommParameters(CommParameters params) {
		return 0;
	}

	@Override
	public void setCallback(TransceiverCallback callback) {
		this.callback = callback;
	}

	@Override
	public TransceiverCallback getCallback() {
		return callback;
	}

	@Override
	public int setTxCommParameters(CommParameters params) {
		this.tx = params;
		return 0;
	}

	@Override
	public int setTxState(TransceiverTxState state) {
		if (state.equals(TransceiverTxState.OFF) && clientSocket != null) {
			closeQuietly(clientSocket);
			clientSocket = null;
		} else if (!state.equals(TransceiverTxState.OFF) && clientSocket == null) {
			try {
				int port = config.getInteger("tcp.transceiver.remote.port", 8081);
				clientSocket = new Socket();
				clientSocket.connect(new InetSocketAddress(config.getProperty("tcp.transceiver.remote.host"), port));
				LOG.info("tx connected to: {}", clientSocket.getInetAddress());
			} catch (IOException e) {
				LOG.error("unable to setup tx connectin", e);
			}
		}
		this.txState = state;
		return 0;
	}

	@Override
	public TransceiverTxState getTxState() {
		return txState;
	}

	@Override
	public int setRxState(TransceiverRxState state) {
		if (state.equals(TransceiverRxState.OFF) && server != null) {
			rxFuture.cancel(true);
			return 0;
		}
		if (state.equals(TransceiverRxState.RECEIVING) && server == null) {
			int port = config.getInteger("tcp.transceiver.port", 8081);
			try {
				String hostname = config.getProperty("tcp.transceiver.host");
				if (hostname == null) {
					hostname = "localhost";
				}
				InetAddress address = InetAddress.getByName(hostname);
				server = new ServerSocket(port, 50, address);
				LOG.info("listening on {}:{}", hostname, port);
				rxFuture = executor.submit(new Runnable() {

					@SuppressWarnings("resource")
					@Override
					public void run() {
						while (!Thread.currentThread().isInterrupted()) {
							try (Socket client = server.accept()) {
								LOG.info("rx client connected: {}:{}", client.getInetAddress(), client.getPort());
								handleConnect();
								read(client.getInputStream());
								LOG.info("rx client disconnected: {}:{}", client.getInetAddress(), client.getPort());
							} catch (java.net.SocketException e) {
								if (e.getMessage().contains("Socket closed")) {
									break;
								}
								LOG.error("unable to handle", e);
							} catch (Exception e) {
								LOG.error("unable to handle", e);
							}
						}

						if (!server.isClosed()) {
							closeQuietly(server);
						}
					}
				});
			} catch (IOException e) {
				LOG.error("unable to start rx", e);
				return 1;
			}
		}
		return 0;
	}

	private void read(InputStream is) {
		TcpCsSublayer csSublayer = new TcpCsSublayer(config, callback);
		while (!Thread.currentThread().isInterrupted()) {
			int actuallyRead;
			try {
				actuallyRead = is.read(buffer);
			} catch (IOException e) {
				LOG.info("remote disconnected", e);
				handleDisconnect();
				return;
			}
			if (actuallyRead == -1) {
				handleDisconnect();
				return;
			}
			csSublayer.handle(buffer, 0, actuallyRead);
		}
	}

	@Override
	public TransceiverRxState getRxState() {
		return null;
	}

	private void handleConnect() {
		if (callback != null) {
			callback.onCarrierAcquired();
		}
		if (callback != null) {
			callback.onSymbolLock();
		}
	}

	private void handleDisconnect() {
		if (callback != null) {
			callback.onSymbolUnlock();
		}
		if (callback != null) {
			callback.onCarrierLost();
		}
	}

	private static void closeQuietly(Closeable c) {
		try {
			c.close();
		} catch (IOException e) {
			// do nothing
		}
	}

	public void setTxDelay(Long txDelay) {
		this.txDelay = txDelay;
	}

	@Override
	public int enableAsmRxCallback(boolean enable) {
		return 0;
	}

	@Override
	public int enableAsmTxCallback(boolean enable) {
		return 0;
	}
}
