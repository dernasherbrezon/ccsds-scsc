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
import com.leosatdata.util.BitInputStream;
import com.leosatdata.util.BitOutputStream;
import com.leosatdata.util.Configuration;

public class FrameSublayer implements Runnable, RxCallback {

	private final static Logger LOG = LoggerFactory.getLogger(FrameSublayer.class);

	private BlockingQueue<Object> userQueue = new ArrayBlockingQueue<>(100);
	private BlockingQueue<Object> controlQueue = new ArrayBlockingQueue<>(100);

	private final Thread t;
	private final Transceiver transceiver;
	private final NodeCallback nodeCallback;
	private int expFsn = 0;
	private int seqCtrlFsn = 0;
	private SpduCallback spduCallback;
	private UserDataCallback userDataCallback;
	private final int localScid;
	private final boolean testSource;
	private int remoteScid;

	public FrameSublayer(Configuration config, Transceiver transceiver, NodeCallback nodeCallback) {
		this.t = new Thread(this, "frame-sublayer");
		this.transceiver = transceiver;
		this.transceiver.setRxCallback(this);
		this.nodeCallback = nodeCallback;
		this.localScid = config.getInteger("local_spacecraft_id", 0);
		this.testSource = config.getBoolean("test_source");
		this.remoteScid = config.getInteger("remote_spacecraft_id", -1);
	}

	@Override
	public void run() {
		while (!Thread.currentThread().isInterrupted()) {
			Object obj;
			try {
				obj = userQueue.take();
			} catch (InterruptedException e) {
				break;
			}
			ByteArrayOutputStream baos = new ByteArrayOutputStream();
			if (obj instanceof com.leosatdata.ccsds2110b6.model.TransferFrame) {
				com.leosatdata.ccsds2110b6.model.TransferFrame frame = (com.leosatdata.ccsds2110b6.model.TransferFrame) obj;
				if (frame.getHeader().getQualityOfService().equals(QualityOfService.EXPEDITED)) {
					if (expFsn > com.leosatdata.ccsds2110b6.util.TransferFrameParser.MAX_SEQUENCE_NUMBER) {
						expFsn = 0;
					}
					frame.getHeader().setFrameSequenceNumber(expFsn++);
				}
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
				try (BitOutputStream bos = new BitOutputStream(baos)) {
					com.leosatdata.ccsds7321b3.util.TransferFrameParser.encode(frame, bos);
				} catch (IOException e1) {
					LOG.error("can't serialize transferframe4", e1);
					continue;
				}
			}
			transceiver.send(baos.toByteArray());
			// FIXME wait until message actually sent
		}
		// FIXME wait for queue
	}

	@Override
	public void onRx(byte[] data) {
		// FIXME support version-2 AOS frames
		Object frame = null;
		PduTypeId type = null;
		SourceDestination sourceDestination = null;
		int scid = 0;
		if (com.leosatdata.ccsds2110b6.util.TransferFrameParser.accept(data)) {
			try {
				com.leosatdata.ccsds2110b6.model.TransferFrame framev3 = com.leosatdata.ccsds2110b6.util.TransferFrameParser.decode(new BitInputStream(data));
				type = framev3.getHeader().getPduTypeId();
				sourceDestination = framev3.getHeader().getSourceOrDestination();
				scid = framev3.getHeader().getScid();
				frame = framev3;
			} catch (IOException e) {
				LOG.error("unable to read transferframe3", e);
				return;
			}
		} else if (com.leosatdata.ccsds7321b3.util.TransferFrameParser.accept(data)) {
			try {
				com.leosatdata.ccsds7321b3.model.TransferFrame framev4 = com.leosatdata.ccsds7321b3.util.TransferFrameParser.decode(new BitInputStream(data));
				type = framev4.getHeader().getProtocolControl();
				sourceDestination = framev4.getHeader().getSourceOrDestination();
				scid = framev4.getHeader().getScid();
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
			spduCallback.onSpdu(frame);
		} else {
			userDataCallback.onUserData(frame);
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
