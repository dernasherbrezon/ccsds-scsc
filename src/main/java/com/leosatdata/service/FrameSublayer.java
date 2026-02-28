package com.leosatdata.service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.leosatdata.ccsds2110b6.model.QualityOfService;
import com.leosatdata.util.BitOutputStream;

public class FrameSublayer implements Runnable, RxCallback {

	private final static Logger LOG = LoggerFactory.getLogger(FrameSublayer.class);

	private BlockingQueue<Object> userQueue = new ArrayBlockingQueue<>(100);
	private BlockingQueue<Object> controlQueue = new ArrayBlockingQueue<>(100);

	private final Thread t;
	private final Transceiver transceiver;
	private int expFsn = 0;
	private SpduCallback spduCallback;
	private UserDataCallback userDataCallback;

	public FrameSublayer(Transceiver transceiver) {
		t = new Thread(this, "frame-sublayer");
		this.transceiver = transceiver;
		this.transceiver.setRxCallback(this);
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
					frame.getHeader().setFrameSequenceNumber(expFsn++);
				}
				// FIXME else
				try (BitOutputStream bos = new BitOutputStream(baos)) {
					com.leosatdata.ccsds2110b6.util.TransferFrameParser.encode(frame, bos);
				} catch (IOException e1) {
					LOG.error("can't serialize transferframe3", e1);
					continue;
				}
			} else if (obj instanceof com.leosatdata.ccsds7321b3.model.TransferFrame) {
				com.leosatdata.ccsds7321b3.model.TransferFrame frame = (com.leosatdata.ccsds7321b3.model.TransferFrame) obj;
				if (frame.getHeader().getSequenceControl().equals(QualityOfService.EXPEDITED)) {
					frame.getHeader().setVcfCount(expFsn++);
				}
				// FIXME else
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
		// FIXME parse and call corresponding callback
	}

	public void start() {
		t.start();
	}

	public void stop() {
		t.interrupt();
		// FIXME wait for termination
	}

	public void sendUserFrame(Object frame) {
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
