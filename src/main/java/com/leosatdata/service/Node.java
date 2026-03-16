package com.leosatdata.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.leosatdata.ccsds2110b6.model.PduTypeId;
import com.leosatdata.ccsds2110b6.model.QualityOfService;
import com.leosatdata.ccsds2110b6.model.SourceDestination;
import com.leosatdata.model.ControllerEvent;
import com.leosatdata.model.ControllerEventType;
import com.leosatdata.model.Event;
import com.leosatdata.model.EventType;
import com.leosatdata.model.State;
import com.leosatdata.util.Configuration;
import com.leosatdata.util.TransferFrameUtil;

public class Node implements Runnable, RxCallback, TxCallback, SymbolInLockCallback, CarrierCallback {

	private final static Logger LOG = LoggerFactory.getLogger(Node.class);

	private final Configuration config;
	private final Transceiver transceiver;
	private final NodeCallback callback;
	private final UserDataCallback rxCallback;
	private final State state;
	private final Mac mac;
	private final FrameSublayer frameSublayer;

	private final Thread t;
	private final int localScid;
	private final boolean testSource;
	private int remoteScid;

	public Node(Configuration config, Transceiver transceiver, NodeCallback callback, UserDataCallback rxCallback) {
		this.config = config;
		this.transceiver = transceiver;
		this.transceiver.setRxCallback(this);
		this.transceiver.setTxCallback(this);
		this.callback = callback;
		this.rxCallback = rxCallback;
		this.localScid = config.getInteger("local_spacecraft_id", 0);
		this.testSource = config.getBoolean("test_source");
		this.remoteScid = config.getInteger("remote_spacecraft_id", -1);
		this.state = new State(config);
		this.mac = new Mac(state, config, transceiver, callback);
		this.frameSublayer = new FrameSublayer(state, config, transceiver, callback);
		t = new Thread(this, "node-thread");
		t.setDaemon(true);
	}

	private void handle(Event e) {
		mac.handle(e);
		frameSublayer.handle(e);
	}

	@Override
	public void run() {
		while (!Thread.currentThread().isInterrupted()) {
			try {
				Event event = state.getQueue().take();
				if (event.getType().equals(EventType.STOP)) {
					break;
				}
				handle(event);
			} catch (InterruptedException e) {
				break;
			} catch (Exception e) {
				LOG.error("unable to handle", e);
				continue;
			}
		}
	}

	public void startSession() {
		sendEvent(new Event(EventType.E2_CONNECTING_T));
	}

	public void waitForSession() {
		sendEvent(new Event(EventType.E1_CONNECTING_L));
	}

	public void stopSession() {
		sendEvent(new Event(EventType.E21_LNMD));
	}

	// going into inactive straightaway
	public void reset() {
		sendEvent(new Event(EventType.E28_INACTIVE));
	}

	public void setCommParameters(Object directive) {
		sendEvent(new Event(EventType.E12_LOCAL_COMM_CHANGE, directive));
	}

	public void send(byte[] data, QualityOfService qos) {
		// will lock if no free space available in queues
		if (qos.equals(QualityOfService.EXPEDITED)) {
			state.getExpQueue().add(data);
		} else {
			state.getSeqQueue().add(data);
		}
	}

	@Override
	public void onRx(byte[] data) {
		Object frame = frameSublayer.decode(data);
		if (frame == null) {
			return;
		}

		frameSublayer.debug(true, data);

		PduTypeId type = TransferFrameUtil.getPduTypeId(frame);
		SourceDestination sourceDestination = TransferFrameUtil.getSourceDestination(frame);
		int scid = TransferFrameUtil.getScid(frame);
		byte[] payload = TransferFrameUtil.getPayload(frame);
		if (type == null || sourceDestination == null || scid < 0 || payload == null) {
			return;
		}

		boolean invalidDestination = sourceDestination.equals(SourceDestination.DESTINATION) && localScid != scid;
		if (invalidDestination) {
			LOG.debug("discard frame to {}", scid);
			return;
		}

		boolean invalidSource = sourceDestination.equals(SourceDestination.SOURCE) && testSource && remoteScid != -1 && remoteScid != scid;
		if (invalidSource) {
			callback.handle(new ControllerEvent(ControllerEventType.INVALID_FRAME_SOURCE, scid));
			return;
		}

		if (sourceDestination.equals(SourceDestination.SOURCE) && testSource && remoteScid == -1) {
			remoteScid = scid;
			LOG.info("test source validation started for {}", remoteScid);
		}

		if (type.equals(PduTypeId.SPDU)) {
			sendEvent(new Event(EventType.SPDU, frameSublayer.decodeSpdu(payload)));
		} else {
			rxCallback.onUserData(payload);
		}
	}

	@Override
	public void onTx() {
		sendEvent(new Event(EventType.TX_COMPLETE));
	}

	@Override
	public void onCarrierAcquired() {
		sendEvent(new Event(EventType.E8_1_CARRIER_RECEIVED));
	}

	@Override
	public void onSymbolInLock() {
		sendEvent(new Event(EventType.E8_2_SYMBOL_IN_LOCK));
	}

	public void start() {
		mac.start();
		t.start();
	}

	public void stop() {
		sendEvent(new Event(EventType.STOP));
		t.interrupt();
		try {
			t.join(config.getDuration("shutdown_duration"));
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
		}
		mac.stop();
	}

	private void sendEvent(Event e) {
		try {
			state.getQueue().put(e);
		} catch (InterruptedException e1) {
			Thread.currentThread().interrupt();
			LOG.error("can't send event", e1);
		}
	}

}
