package com.leosatdata.service;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.LinkedBlockingQueue;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.leosatdata.model.ControlParameters;
import com.leosatdata.model.Event;
import com.leosatdata.model.EventType;
import com.leosatdata.model.MacDuplex;
import com.leosatdata.model.MacMode;
import com.leosatdata.model.PlExtensions;
import com.leosatdata.model.TxRxParameters;
import com.leosatdata.util.Configuration;
import com.leosatdata.util.SpduParser;

public class Mac implements Runnable {

	private final static Logger LOG = LoggerFactory.getLogger(Mac.class);

	private final Fop fop;
	private final Farm farm;
	private final Transceiver transceiver;
	private final Configuration config;
	private final NodeCallback callback;

	private final LinkedBlockingQueue<Event> queue = new LinkedBlockingQueue<>();
	private Timer waitTimer;

	private MacMode mode = MacMode.INACTIVE;
	private boolean transmit = false;
	private boolean persistence = false;
	private MacDuplex duplex = MacDuplex.FULL; // FIXME check defaults
	private int ss;
	private int x;
	private int y;
	private int z;
	private final Thread t;

	private byte[] commValueBuffer;

	public Mac(Configuration config, Transceiver transceiver, NodeCallback callback) {
		this.config = config;
		this.callback = callback;
		this.transceiver = transceiver;
		this.fop = new Fop(transceiver);
		this.farm = new Farm();
		t = new Thread(this, "mac-thread");
	}

	public void start() {
		t.start();
		waitTimer = new Timer("wt");
//		fop.start();
//		// FIXME start fop / farm
	}

	@Override
	public void run() {
		while (!Thread.currentThread().isInterrupted()) {
			try {
				handle(queue.take());
			} catch (InterruptedException e) {
				break;
			}
		}
	}

	public void stop() {
		t.interrupt();
		try {
			// FIXME termination timeout
			t.join();
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
		}
	}

	private void handle(Event e) {
		switch (e.getType()) {
		case E2_CONNECTING_T:
			// FIXME create hail directives
			ByteArrayOutputStream baos = new ByteArrayOutputStream();
			DataOutputStream dos = new DataOutputStream(baos);
//			SpduParser.encode(dos, new Object());
			try {
				dos.close();
			} catch (IOException e1) {
				// ignore
			}
			commValueBuffer = baos.toByteArray();

			persistence = true;
			mode = MacMode.CONNECTING_T;
			transceiver.startCarrier();
			scheduleWait(EventType.E4_CARRIER_ONLY_TIMEOUT, config.getDuration("carrier_only_duration"));
			break;
		case E4_CARRIER_ONLY_TIMEOUT:
			transceiver.startIdle();
			scheduleWait(EventType.E5_ACQUISITION_IDLE_TIMEOUT, config.getDuration("acquisition_idle_duration"));
			break;
		case E5_ACQUISITION_IDLE_TIMEOUT:
			transceiver.send(commValueBuffer);
			break;
		case E6_OUTPUT_FIFO_EMPTY:
			scheduleWait(EventType.E7_TAIL_IDLE_TIMEOUT, config.getDuration("tail_idle_duration"));
			break;
		case E7_TAIL_IDLE_TIMEOUT:
			scheduleWait(EventType.E8_HAIL_WAIT_TIMEOUT, config.getDuration("hail_wait_duration"));
			break;
		default:
			LOG.error("unknown event type: {}", e.getType());
		}
	}

//
//	public void send(TransferFrame frame) {
//		if (frame == null || frame.getHeader() == null) {
//			return;
//		}
//		// accept only user data from users
//		if (frame.getHeader().getPduTypeId().equals(PduTypeId.SPDU)) {
//			// FIXME some error reporting?
//			return;
//		}
//		fop.send(frame);
//	}
//

	// SET MODE directive
	public void setMode(MacMode mode) {
		switch (mode) {
		case INACTIVE:
			transceiver.setModulation(false);
			transceiver.stopRx();
			transceiver.stopTx();
			transmit = false;
			persistence = false;
			ss = 0;
			x = 0;
			y = 0;
			z = 0;
			// cancel all timers
			// reset sequence counters
			break;
		case CONNECTING_T:
			sendEvent(new Event(EventType.E2_CONNECTING_T));
			break;
		case CONNECTING_L:
			transceiver.startRx();
			transceiver.stopTx();
			break;
		// FIXME active?
		default:
			break;
		}
	}

	// SET INITIALIZE MODE directive
	public void setInitializeMode() {
		setMode(MacMode.INACTIVE);
		fop.setInitializeMode();
		farm.setInitializeMode();
	}

	public void setLocalCommChange(TxRxParameters tx, TxRxParameters rx, PlExtensions ext) {
//		transceiver.setTxParameters(tx);
//		transceiver.setRxParameters(tx);
	}

	public void setCommunicationsValueBuffer() {

	}

	// This directive initiates the session termination process.
	public void setNoMoreData() {

	}

	public void setDuplex(MacDuplex duplex) {
		this.duplex = duplex;
	}

	public void setReceivingScidBuffer() {

	}

	public void readStatus() {
		// FIXME Read Status shall selectively read the local status registers and
		// buffers (including timing services) within the transceiver.
	}

	// remote
	void setTxParameters(TxRxParameters tx) {

	}

	void setRxParameters(TxRxParameters rx) {

	}

	void setPlExtensions(PlExtensions ext) {

	}

	void setControlParameters(ControlParameters params) {

	}

	private void sendEvent(Event e) {
		try {
			queue.put(e);
		} catch (InterruptedException e1) {
			Thread.currentThread().interrupt();
			LOG.error("can't send event", e1);
		}
	}

	private void scheduleWait(EventType type, long duration) {
		waitTimer.schedule(new TimerTask() {

			@Override
			public void run() {
				sendEvent(new Event(type));
			}
		}, duration);
	}
}
