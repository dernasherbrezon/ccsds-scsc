package com.leosatdata.service;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.leosatdata.ccsds2110b6.model.PduTypeId;
import com.leosatdata.ccsds2110b6.model.QualityOfService;
import com.leosatdata.ccsds2110b6.model.SourceDestination;
import com.leosatdata.model.ControllerEvent;
import com.leosatdata.model.ControllerEventType;
import com.leosatdata.model.Duplex;
import com.leosatdata.model.Event;
import com.leosatdata.model.EventType;
import com.leosatdata.model.QueueType;
import com.leosatdata.model.State;
import com.leosatdata.util.Configuration;
import com.leosatdata.util.SpduUtil;
import com.leosatdata.util.TransferFrameUtil;

public class Node implements Runnable, RxCallback, TxCallback, SymbolLockCallback, CarrierCallback {

	private final static Logger LOG = LoggerFactory.getLogger(Node.class);

	private final Configuration config;
	private final Transceiver transceiver;
	private final NodeCallback callback;
	private final UserDataCallback rxCallback;
	private final State state;
	private final FullDuplexMac fullDuplexMac;
	private final HalfDuplexMac halfDuplexMac;
	private final FrameSublayer frameSublayer;
	private final TimerService timer;

	private final Thread t;
	private final int localScid;
	private final boolean testSource;
	private final int maxSequenceNumber;
	private int remoteScid;
	// there are 2 vr variables. One is thread-local onRx, the other one is in State
	// this is done to synchronize the state across 2 threads
	private int vrFromCallback = 0;

	public Node(Configuration config, Transceiver transceiver, NodeCallback callback, UserDataCallback rxCallback) {
		this.config = config;
		this.transceiver = transceiver;
		this.transceiver.setRxCallback(this);
		this.transceiver.setTxCallback(this);
		this.transceiver.setCarrierCallback(this);
		this.transceiver.setSymbolLockCallback(this);
		this.callback = callback;
		this.rxCallback = rxCallback;
		this.localScid = config.getInteger("local_spacecraft_id", 0);
		this.testSource = config.getBoolean("test_source");
		this.remoteScid = config.getInteger("remote_spacecraft_id", -1);
		this.maxSequenceNumber = (config.getInteger("maximum_frame_sequence_length", 0) == 1) ? 0xFF : 0xFFFF;
		this.state = new State(config);
		this.timer = new TimerService(state, config);
		this.fullDuplexMac = new FullDuplexMac(state, config, transceiver, callback, timer);
		this.halfDuplexMac = new HalfDuplexMac(state, config, transceiver, callback, timer);
		this.frameSublayer = new FrameSublayer(state, config, transceiver, callback);
		t = new Thread(this, "node-" + localScid);
		t.setDaemon(true);
	}

	@SuppressWarnings("incomplete-switch")
	private void handle(Event e) {
		switch (e.getType()) {
		case DUPLEX:
			state.setDuplex((Duplex) e.getArg());
			if (state.getCommParameters() != null) {
				SpduUtil.setDuplex(state.getCommParameters(), state.getDuplex());
			}
			return;
		case CARRIER_ACQUIRED:
			state.setCarrierAcquired(true);
			timer.cancelCarrierLoss();
			break;
		case CARRIER_LOST:
			state.setCarrierAcquired(false);
			break;
// the below TODO can be replaced by hashmap
		case CONNECTING_L:
			if (state.getDuplex().equals(Duplex.FULL_DUPLEX)) {
				e = new Event(EventType.E1_CONNECTING_L);
			} else if (state.getDuplex().equals(Duplex.HALF_DUPLEX)) {
				e = new Event(EventType.E29_CONNECTING_L);
			}
			break;
		case CONNECTING_T:
			if (state.getDuplex().equals(Duplex.FULL_DUPLEX)) {
				e = new Event(EventType.E2_CONNECTING_T);
			} else if (state.getDuplex().equals(Duplex.HALF_DUPLEX)) {
				e = new Event(EventType.E31_CONNECTING_T);
			}
			break;
		case LOCAL_COMM_CHANGE:
			if (state.getDuplex().equals(Duplex.FULL_DUPLEX)) {
				e = new Event(EventType.E12_LOCAL_COMM_CHANGE, e.getArg());
			} else if (state.getDuplex().equals(Duplex.HALF_DUPLEX)) {
				e = new Event(EventType.E62_LOCAL_COMM_CHANGE, e.getArg());
			}
			break;
		case LNMD:
			if (state.getDuplex().equals(Duplex.FULL_DUPLEX)) {
				e = new Event(EventType.E21_LNMD);
			} else if (state.getDuplex().equals(Duplex.HALF_DUPLEX)) {
				e = new Event(EventType.E51_LNMD);
			}
			break;
		}
		if (state.getDuplex().equals(Duplex.FULL_DUPLEX)) {
			fullDuplexMac.handle(e);
		} else if (state.getDuplex().equals(Duplex.HALF_DUPLEX)) {
			halfDuplexMac.handle(e);
		}
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
		sendEvent(new Event(EventType.CONNECTING_T));
	}

	public void waitForSession() {
		sendEvent(new Event(EventType.CONNECTING_L));
	}

	public void stopSession() {
		sendEvent(new Event(EventType.LNMD));
	}

	// going into inactive straightaway
	public void reset() {
		sendEvent(new Event(EventType.E28_INACTIVE));
	}

	public void setCommParameters(Object directive) {
		if (directive == null) {
			throw new IllegalArgumentException("comm parameters cannot be null");
		}
		sendEvent(new Event(EventType.LOCAL_COMM_CHANGE, directive));
	}

	public void clearQueue(QueueType type) {
		if (type == null) {
			throw new IllegalArgumentException("queue type cannot be null");
		}
		sendEvent(new Event(EventType.CLEAR_QUEUE, type));
	}

	public void setDuplex(Duplex duplex) {
		if (duplex == null) {
			throw new IllegalArgumentException("duplex cannot be null");
		}
		sendEvent(new Event(EventType.DUPLEX, duplex));
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
			for (Object cur : frames) {
				try {
					state.getExpQueue().put(cur);
				} catch (InterruptedException e) {
					Thread.currentThread().interrupt();
					return;
				}
			}
		} else {
			for (Object cur : frames) {
				try {
					state.getSeqQueue().put(cur);
				} catch (InterruptedException e) {
					Thread.currentThread().interrupt();
					return;
				}
			}
		}
		// event queue might be polluted by these events
		// when persistent activity is ongoing and queues not processed
		// shouldn't be a big issue to discard them and start processing queues
		// naturally
		sendEvent(new Event(EventType.UFRAME));
	}

	@Override
	public void onRx(byte[] data) {
		Object frame = frameSublayer.onRx(data);
		if (frame == null) {
			return;
		}

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
					vrFromCallback = (vrFromCallback + 1) & maxSequenceNumber;
					sendEvent(new Event(EventType.RE4_SEQUENCE_FRAME, vrFromCallback));
				} else if (frameSublayer.modCompare(ns, vrFromCallback) > 0) {
					sendEvent(new Event(EventType.RE5_GAP_DETECTED));
					acceptFrame = false;
					LOG.info("gap detected. expected: {} got: {}", vrFromCallback, ns);
				} else if (frameSublayer.modCompare(ns, vrFromCallback) < 0) {
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
		sendEvent(new Event(EventType.CARRIER_ACQUIRED));
	}

	@Override
	public void onCarrierLost() {
		sendEvent(new Event(EventType.CARRIER_LOST));
	}

	@Override
	public void onSymbolLock() {
		sendEvent(new Event(EventType.SYMBOL_LOCK));
	}

	@Override
	public void onSymbolUnlock() {
		sendEvent(new Event(EventType.SYMBOL_UNLOCK));
	}

	public void start() {
		frameSublayer.start();
		timer.start();
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
		timer.stop();
		frameSublayer.stop();
	}

	private void sendEvent(Event e) {
		try {
			state.getQueue().put(e);
		} catch (InterruptedException e1) {
			Thread.currentThread().interrupt();
		}
	}

	// used on tests only
	State getState() {
		return state;
	}

	void setVrFromCallback(int vrFromCallback) {
		this.vrFromCallback = vrFromCallback;
	}

}
