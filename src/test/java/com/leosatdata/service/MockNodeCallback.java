package com.leosatdata.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.leosatdata.model.ControllerEvent;
import com.leosatdata.model.ControllerEventType;

public class MockNodeCallback implements NodeCallback {

	private final static Logger LOG = LoggerFactory.getLogger(MockNodeCallback.class);

	private final Object lock = new Object();
	private ControllerEvent event;

	@Override
	public void handle(ControllerEvent event) {
		synchronized (lock) {
			if (this.event != null) {
				LOG.info("replacing: {} with {}", this.event, event);
			}
			this.event = event;
			lock.notifyAll();
		}
	}

	public ControllerEvent waitForEvent(ControllerEventType type) {
		return waitForEvent(15000, type);
	}

	public ControllerEvent waitForEvent(long timeout, ControllerEventType type) {
		synchronized (lock) {
			long remaining = timeout;
			while (remaining > 0) {
				long start = System.currentTimeMillis();
				try {
					if (event != null && event.getType().equals(type)) {
						ControllerEvent result = event;
						event = null;
						return result;
					}
					lock.wait(remaining);
					remaining = System.currentTimeMillis() - start;
					if (remaining > 0) {
						continue;
					}
					ControllerEvent result = event;
					event = null;
					if (result != null && result.getType().equals(type)) {
						return result;
					}
					return null;
				} catch (InterruptedException e) {
					Thread.currentThread().interrupt();
					return null;
				}
			}
			return null;
		}
	}

}
