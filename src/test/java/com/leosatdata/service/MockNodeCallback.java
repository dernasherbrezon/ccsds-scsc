package com.leosatdata.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.leosatdata.model.ControllerEvent;
import com.leosatdata.model.ControllerEventType;
import com.leosatdata.model.PersistentActivity;
import com.leosatdata.model.PersistentActivityType;

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
		return waitForEvent(20000, type, null);
	}

	public ControllerEvent waitForEvent(ControllerEventType type, PersistentActivityType persistentType) {
		return waitForEvent(20000, type, persistentType);
	}

	public ControllerEvent waitForEvent(long timeout, ControllerEventType type, PersistentActivityType persistentType) {
		synchronized (lock) {
			long remaining = timeout;
			while (remaining > 0) {
				long start = System.currentTimeMillis();
				try {
					if (match(event, type, persistentType)) {
						ControllerEvent result = event;
						event = null;
						return result;
					}
					lock.wait(remaining);
					long actualWait = System.currentTimeMillis() - start;
					ControllerEvent result = event;
					event = null;
					if (match(result, type, persistentType)) {
						return result;
					}
//					if (result != null && result.getType().equals(type)) {
//						return result;
//					}
					remaining -= actualWait;
					if (remaining > 0) {
						continue;
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

	private static boolean match(ControllerEvent event, ControllerEventType type, PersistentActivityType persistentType) {
		if (event == null) {
			return false;
		}
		if (!event.getType().equals(type)) {
			return false;
		}
		if (!event.getType().equals(ControllerEventType.PERSISTENT_ACTIVITY)) {
			return true;
		}
		// do not match further. if type is not provided, then consider OK
		if (persistentType == null) {
			return true;
		}
		PersistentActivity activity = (PersistentActivity) event.getObj();
		// shouldn't be null
		if (activity == null) {
			return false;
		}
		return activity.getNotification().equals(persistentType);
	}

}
