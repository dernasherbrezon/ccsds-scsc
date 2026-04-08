package com.leosatdata.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.leosatdata.model.ControllerEvent;

public class MockNodeCallback implements NodeCallback {
	
	private final static Logger LOG = LoggerFactory.getLogger(MockNodeCallback.class);

	private final Object lock = new Object();
	private ControllerEvent event;

	@Override
	public void handle(ControllerEvent event) {
		synchronized (lock) {
			LOG.info("got event " + event.getType() + " old event: ", this.event);
			this.event = event;
			lock.notifyAll();
		}
	}

	public ControllerEvent waitForEvent() {
		synchronized (lock) {
			try {
				if (event != null) {
					ControllerEvent result = event;
					event = null;
					return result;
				}
				LOG.info("waiting");
				// ignore spurious wake-ups
				lock.wait(15000);
				LOG.info("unlocked");
				ControllerEvent result = event;
				event = null;
				return result;
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
				return null;
			}
		}
	}

}
