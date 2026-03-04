package com.leosatdata.service2;

import java.io.IOException;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;

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
import com.leosatdata.model.MacMode;
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
import com.leosatdata.util.BitInputStream;
import com.leosatdata.util.Configuration;

public class Node implements Runnable, RxCallback, TxCallback, SymbolInLockCallback, CarrierCallback {

	private final static Logger LOG = LoggerFactory.getLogger(Node.class);

	private final BlockingQueue<Object> seqQueue;
	private final BlockingQueue<Object> expQueue;

	private final Configuration config;
	private final Transceiver transceiver;
	private final NodeCallback callback;
	private final UserDataCallback rxCallback;
	private final com.leosatdata.ccsds7320b4.util.TransferFrameParser framev2Parser;
	private final com.leosatdata.ccsds7321b3.util.TransferFrameParser framev4Parser;
	private final State state;
	private final Mac2 mac;

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
		this.framev2Parser = new com.leosatdata.ccsds7320b4.util.TransferFrameParser(config);
		this.framev4Parser = new com.leosatdata.ccsds7321b3.util.TransferFrameParser(config);
		this.seqQueue = new ArrayBlockingQueue<>(config.getInteger("transmission_window", 10));
		this.expQueue = new ArrayBlockingQueue<>(config.getInteger("transmission_window", 10)); // use the same parameter for expedited and sequenced

		this.state = new State();
		this.mac = new Mac2(state, config, transceiver, callback);
		t = new Thread(this, "node-thread");
	}

	public void start() {
		mac.start();
		t.start();
	}

	private void handle(Event e) {
		mac.handle(e);
	}

	@Override
	public void run() {
		while (!Thread.currentThread().isInterrupted()) {
			try {
				handle(state.getQueue().take());
			} catch (InterruptedException e) {
				break;
			}
		}
	}

	public void setMode(MacMode mode) {
		switch (mode) {
		case CONNECTING_T:
			sendEvent(new Event(EventType.E2_CONNECTING_T));
			break;

		default:
			break;
		}
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
		// FIXME create U-frame put into the queue
		// will lock if no free space available in queues
		if (qos.equals(QualityOfService.EXPEDITED)) {
			expQueue.add(data);
		} else {
			seqQueue.add(data);
		}
	}

	@Override
	public void onRx(byte[] data) {
		byte[] payload = null;
		PduTypeId type = null;
		SourceDestination sourceDestination = null;
		int scid = 0;
		if (com.leosatdata.ccsds7320b4.util.TransferFrameParser.accept(data)) {
			try {
				com.leosatdata.ccsds7320b4.model.TransferFrame framev2 = framev2Parser.decode(new BitInputStream(data));
				type = PduTypeId.USER_DATA; // only u-frames
				sourceDestination = SourceDestination.UNKNOWN; // ignore scid validation as per spec
				scid = framev2.getHeader().getScid();
				payload = framev2.getDataField();
			} catch (IOException e) {
				LOG.error("unable to read transferframe2", e);
				return;
			}
		} else if (com.leosatdata.ccsds2110b6.util.TransferFrameParser.accept(data)) {
			try {
				com.leosatdata.ccsds2110b6.model.TransferFrame framev3 = com.leosatdata.ccsds2110b6.util.TransferFrameParser.decode(new BitInputStream(data));
				type = framev3.getHeader().getPduTypeId();
				sourceDestination = framev3.getHeader().getSourceOrDestination();
				scid = framev3.getHeader().getScid();
				payload = framev3.getDataField();
			} catch (IOException e) {
				LOG.error("unable to read transferframe3", e);
				return;
			}
		} else if (com.leosatdata.ccsds7321b3.util.TransferFrameParser.accept(data)) {
			try {
				com.leosatdata.ccsds7321b3.model.TransferFrame framev4 = framev4Parser.decode(new BitInputStream(data));
				type = framev4.getHeader().getProtocolControl();
				sourceDestination = framev4.getHeader().getSourceOrDestination();
				scid = framev4.getHeader().getScid();
				payload = framev4.getDataField();
			} catch (IOException e) {
				LOG.error("unable to read transferframe4", e);
				return;
			}
		} else {
			// data is somehow corrupted and completely unsupported
			LOG.debug("invalid frame received");
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

	private void sendEvent(Event e) {
		try {
			state.getQueue().put(e);
		} catch (InterruptedException e1) {
			Thread.currentThread().interrupt();
			LOG.error("can't send event", e1);
		}
	}

}
