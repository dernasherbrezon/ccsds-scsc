package com.leosatdata.service;

import java.io.ByteArrayOutputStream;
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
import com.leosatdata.model.TransceiverEvent;
import com.leosatdata.model.Type1Directive;
import com.leosatdata.model.Type5Directive;
import com.leosatdata.util.BitInputStream;
import com.leosatdata.util.BitOutputStream;
import com.leosatdata.util.Configuration;
import com.leosatdata.util.TransceiverUtil;

public class FrameSublayer implements Runnable, RxCallback {

	private final static Logger LOG = LoggerFactory.getLogger(FrameSublayer.class);

	private BlockingQueue<Object> userQueue = new ArrayBlockingQueue<>(100);
	private BlockingQueue<Object> controlQueue = new ArrayBlockingQueue<>(100);
	private BlockingQueue<Object> currentQueue = userQueue;

	private final Thread t;
	private final Transceiver transceiver;
	private final NodeCallback nodeCallback;
	private final com.leosatdata.ccsds7320b4.util.TransferFrameParser framev2Parser;
	private final com.leosatdata.ccsds7321b3.util.TransferFrameParser framev4Parser;
	private final Configuration config;
	private int expFsn = 0; // VE(S)sequence number of the next Expedited frame to be sent
	private int seqCtrlFsn = 0;
	private SpduCallback spduCallback;
	private UserDataCallback userDataCallback;
	private final int localScid;
	private final boolean testSource;
	private int remoteScid;

	public FrameSublayer(Configuration config, Transceiver transceiver, NodeCallback nodeCallback) {
		this.config = config;
		this.t = new Thread(this, "frame-sublayer");
		this.transceiver = transceiver;
		this.transceiver.setRxCallback(this);
		this.nodeCallback = nodeCallback;
		this.localScid = config.getInteger("local_spacecraft_id", 0);
		this.testSource = config.getBoolean("test_source");
		this.remoteScid = config.getInteger("remote_spacecraft_id", -1);
		this.framev2Parser = new com.leosatdata.ccsds7320b4.util.TransferFrameParser(config);
		this.framev4Parser = new com.leosatdata.ccsds7321b3.util.TransferFrameParser(config);
	}

	@Override
	public void run() {
		while (!Thread.currentThread().isInterrupted()) {
			Object obj;
			try {
				obj = currentQueue.take();
			} catch (InterruptedException e) {
				break;
			}
			if (obj instanceof TransceiverEvent) {
				handle((TransceiverEvent) obj);
				continue;
			}
			PduTypeId type;
			ByteArrayOutputStream baos = new ByteArrayOutputStream();
			if (obj instanceof com.leosatdata.ccsds7320b4.model.TransferFrame) {
				com.leosatdata.ccsds7320b4.model.TransferFrame frame = (com.leosatdata.ccsds7320b4.model.TransferFrame) obj;
				if (expFsn > com.leosatdata.ccsds7320b4.util.TransferFrameParser.MAX_SEQUENCE_NUMBER) {
					expFsn = 0;
					if (frame.getHeader().isVcFrameCountCycleUsed()) {
						int frameCountCycle = frame.getHeader().getVcFrameCount() + 1;
						if (frameCountCycle > com.leosatdata.ccsds7320b4.util.TransferFrameParser.MAX_CYCLE_COUNT) {
							frameCountCycle = 0;
						}
						frame.getHeader().setVcFrameCountCycle(frameCountCycle);
					}
				}
				type = PduTypeId.USER_DATA;
				frame.getHeader().setVcFrameCount(expFsn++);
				try (BitOutputStream bos = new BitOutputStream(baos)) {
					framev2Parser.encode(frame, bos);
				} catch (IOException e1) {
					LOG.error("can't serialize transferframe2", e1);
					continue;
				}
			} else if (obj instanceof com.leosatdata.ccsds2110b6.model.TransferFrame) {
				com.leosatdata.ccsds2110b6.model.TransferFrame frame = (com.leosatdata.ccsds2110b6.model.TransferFrame) obj;
				if (frame.getHeader().getQualityOfService().equals(QualityOfService.EXPEDITED)) {
					if (expFsn > com.leosatdata.ccsds2110b6.util.TransferFrameParser.MAX_SEQUENCE_NUMBER) {
						expFsn = 0;
					}
					frame.getHeader().setFrameSequenceNumber(expFsn++);
				}
				type = frame.getHeader().getPduTypeId();
				try (BitOutputStream bos = new BitOutputStream(baos)) {
					com.leosatdata.ccsds2110b6.util.TransferFrameParser.encode(frame, bos);
				} catch (IOException e1) {
					LOG.error("can't serialize transferframe3", e1);
					continue;
				}
			} else if (obj instanceof com.leosatdata.ccsds7321b3.model.TransferFrame) {
				com.leosatdata.ccsds7321b3.model.TransferFrame frame = (com.leosatdata.ccsds7321b3.model.TransferFrame) obj;
				if (frame.getHeader().getSequenceControl().equals(QualityOfService.EXPEDITED)) {
					frame.getHeader().setVcfCountLength(com.leosatdata.ccsds7321b3.util.TransferFrameParser.VCF_COUNT_LENGTH);
					if (expFsn > com.leosatdata.ccsds7321b3.util.TransferFrameParser.MAX_SEQUENCE_NUMBER) {
						expFsn = 0;
					}
					frame.getHeader().setVcfCount(expFsn++);
				}
				type = frame.getHeader().getProtocolControl();
				try (BitOutputStream bos = new BitOutputStream(baos)) {
					framev4Parser.encode(frame, bos);
				} catch (IOException e1) {
					LOG.error("can't serialize transferframe4", e1);
					continue;
				}
			} else {
				LOG.info("unknown frame type: {}", obj);
				continue;
			}
			transceiver.send(baos.toByteArray());
			// FIXME wait until message actually sent
			if (type.equals(PduTypeId.SPDU)) {
				spduCallback.onSpduTx();
			}
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
			nodeCallback.handle(new ControllerEvent(ControllerEventType.INVALID_FRAME_SOURCE, scid));
			return;
		}

		if (sourceDestination.equals(SourceDestination.SOURCE) && testSource && remoteScid == -1) {
			remoteScid = scid;
		}

		if (type.equals(PduTypeId.SPDU)) {
			spduCallback.onSpdu(payload);
		} else {
			userDataCallback.onUserData(payload);
		}
	}

	private void handle(TransceiverEvent event) {
		switch (event.getType()) {
		case START_CARRIER:
			// equal to persistence=true, i.e. start persistence process
			currentQueue = controlQueue;
			String frameVersion = config.getProperty("frame_version");
			if (frameVersion.equals("3")) {
				TransceiverUtil.applyProximityParameters((Type1Directive) event.getObj(), config, transceiver);
			} else if (frameVersion.equals("4")) {
				TransceiverUtil.applyUslpParameters((Type5Directive) event.getObj(), transceiver);
			} else {
				throw new IllegalArgumentException("unsupported frame_version: " + frameVersion);
			}
			transceiver.startCarrier();
			break;
		case START_IDLE:
			transceiver.startIdle();
			break;
		case START_RX:
			transceiver.stopIdle();
			transceiver.startRx();
			break;
		case SWITCH_TO_USER:
			// equal to persistence=false
			currentQueue = userQueue;
			break;
		default:
			LOG.error("unsupported event type: {}", event.getType());
		}
	}

	public void start() {
		t.start();
	}

	public void stop() {
		t.interrupt();
		// FIXME wait for termination
	}

	public void sendUserFrame(Object frame) {
		// FIXME seq ctrl fsn is set before going into the queue
		userQueue.add(frame);
	}

	public void sendControlFrame(Object frame) {
		controlQueue.add(frame);
	}

	public void setSpduCallback(SpduCallback spduCallback) {
		this.spduCallback = spduCallback;
	}

	public void setUserDataCallback(UserDataCallback userDataCallback) {
		this.userDataCallback = userDataCallback;
	}
}
