package com.leosatdata.service2;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.leosatdata.ccsds2110b6.model.PduTypeId;
import com.leosatdata.ccsds2110b6.model.QualityOfService;
import com.leosatdata.ccsds2110b6.model.SourceDestination;
import com.leosatdata.model.ControllerEvent;
import com.leosatdata.model.ControllerEventType;
import com.leosatdata.model.Event;
import com.leosatdata.model.EventType;
import com.leosatdata.model.Link2Establishment;
import com.leosatdata.model.LinkEstablishment;
import com.leosatdata.model.PlExtensions;
import com.leosatdata.model.State;
import com.leosatdata.model.TxRxParameters;
import com.leosatdata.model.Type1Directive;
import com.leosatdata.model.Type4Directive;
import com.leosatdata.model.Type5Directive;
import com.leosatdata.service.CarrierCallback;
import com.leosatdata.service.NodeCallback;
import com.leosatdata.service.RxCallback;
import com.leosatdata.service.SymbolInLockCallback;
import com.leosatdata.service.Transceiver;
import com.leosatdata.service.TxCallback;
import com.leosatdata.service.UserDataCallback;
import com.leosatdata.util.Configuration;

public class Node2 implements Runnable, RxCallback, TxCallback, SymbolInLockCallback, CarrierCallback {

	private final static Logger LOG = LoggerFactory.getLogger(Node2.class);

	private final Configuration config;
	private final Transceiver transceiver;
	private final NodeCallback callback;
	private final UserDataCallback rxCallback;
	private final State state;
	private final Mac2 mac;
	private final FrameSublayer2 frameSublayer;

	private final Thread t;
	private final int localScid;
	private final boolean testSource;
	private int remoteScid;

	public Node2(Configuration config, Transceiver transceiver, NodeCallback callback, UserDataCallback rxCallback) {
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
		this.mac = new Mac2(state, config, transceiver, callback);
		this.frameSublayer = new FrameSublayer2(state, config, transceiver);
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

	public void setLocalCommChange(TxRxParameters tx, TxRxParameters rx, PlExtensions txExt, PlExtensions rxExt) {
		if (!config.getProperty("frame_version").equals("3")) {
			throw new IllegalArgumentException("expected proximity-1");
		}
		Type1Directive obj = new Type1Directive();
		obj.setRxPlExtensions(rxExt);
		obj.setTxPlExtensions(txExt);
		obj.setRxParams(rx);
		obj.setTxParams(tx);
		sendEvent(new Event(EventType.E12_LOCAL_COMM_CHANGE, obj));
	}

	public void setLocalCommChange(LinkEstablishment rxLink, LinkEstablishment txLink) {
		// FIXME when to send type4 vs type5?
		if (!config.getProperty("frame_version").equals("4")) {
			throw new IllegalArgumentException("expected USLP");
		}
		Type4Directive obj = new Type4Directive();
		obj.setRxLink(rxLink);
		obj.setTxLink(txLink);
		sendEvent(new Event(EventType.E12_LOCAL_COMM_CHANGE, obj));
	}

	public void setLocalCommChange(Link2Establishment rxLink, Link2Establishment txLink) {
		if (!config.getProperty("frame_version").equals("4")) {
			throw new IllegalArgumentException("expected USLP");
		}
		Type5Directive obj = new Type5Directive();
		obj.setRxLink(rxLink);
		obj.setTxLink(txLink);
		sendEvent(new Event(EventType.E12_LOCAL_COMM_CHANGE, obj));
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

		PduTypeId type = FrameSublayer2.getPduTypeId(frame);
		SourceDestination sourceDestination = FrameSublayer2.getSourceDestination(frame);
		int scid = FrameSublayer2.getScid(frame);
		byte[] payload = FrameSublayer2.getPayload(frame);
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
			sendEvent(new Event(EventType.SPDU, payload));
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
		t.interrupt();
		sendEvent(new Event(EventType.STOP));
		try {
			t.join(config.getDuration("shutdown_duration"));
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
		}
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
