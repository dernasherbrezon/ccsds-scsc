package com.leosatdata.service;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.leosatdata.ccsds2110b6.model.PduTypeId;
import com.leosatdata.ccsds2110b6.model.QualityOfService;
import com.leosatdata.ccsds2110b6.model.SourceDestination;
import com.leosatdata.model.ControllerEvent;
import com.leosatdata.model.ControllerEventType;
import com.leosatdata.model.Event;
import com.leosatdata.model.EventType;
import com.leosatdata.model.QueueType;
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
	// there are 2 vr variables. One is thread-local onRx, the other one is in State
	// this is done to synchronize the state across 2 threads
	private int vrFromCallback = 0;

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
				if (LOG.isDebugEnabled()) {
					LOG.debug("got event: {} queue size: {}", event.getType(), state.getQueue().size());
				}
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

	public void clearQueue(QueueType type) {
		sendEvent(new Event(EventType.CLEAR_QUEUE, type));
	}

	// TODO pcid parameter? should it be hardcoded local? or specific remote pcid?
	@SuppressWarnings("unchecked")
	public void send(byte[] packet, QualityOfService qos, int portId) {
		if (portId > 0b111) {
			throw new IllegalArgumentException("port id cannot exceed: " + 0b111);
		}
		List<Object> frames = frameSublayer.createUFrame(packet, qos, portId);
		// will lock if no free space available in queues
		if (qos.equals(QualityOfService.EXPEDITED)) {
			state.getExpQueue().addAll(frames);
		} else {
			state.getSeqQueue().addAll(frames);
		}
		// event queue might be polluted by these events
		// when persistent activity is ongoing and queues not processed
		// shouldn't be a big issue to discard them and start processing queues
		// naturally
		sendEvent(new Event(EventType.UFRAME));
	}

	@Override
	public void onRx(byte[] data) {
		Object frame = frameSublayer.decode(data);
		if (frame == null) {
			return;
		}

		frameSublayer.debug(true, frame);

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

		// TODO discard idle packets?

		if (type.equals(PduTypeId.SPDU)) {
			sendEvent(new Event(EventType.SPDU, frameSublayer.decodeSpdu(payload)));
		} else {
			QualityOfService qos = TransferFrameUtil.getQualityOfService(frame);
			boolean acceptFrame = true;
			if (qos.equals(QualityOfService.EXPEDITED)) {
				sendEvent(new Event(EventType.RE3_EXPEDITED_FRAME));
			} else {
				int ns = TransferFrameUtil.getSequenceNumber(frame);
				if (vrFromCallback == ns) {
					vrFromCallback++;
					sendEvent(new Event(EventType.RE4_SEQUENCE_FRAME, vrFromCallback));
				} else if (ns > vrFromCallback) {
					sendEvent(new Event(EventType.RE5_GAP_DETECTED));
					acceptFrame = false;
					LOG.info("gap detected. expected: {} got: {}", vrFromCallback, ns);
				} else if (ns < vrFromCallback) {
					// progressive retransmission will re-send frames until corresponding PLCW
					// received
					acceptFrame = false;
				}
			}
			if (acceptFrame) {
				byte[] packet = frameSublayer.assemblePacket(frame);
				if (packet != null) {
					rxCallback.onUserData(packet, TransferFrameUtil.getPortId(frame));
				}
			}
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
		frameSublayer.start();
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
		frameSublayer.stop();
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
