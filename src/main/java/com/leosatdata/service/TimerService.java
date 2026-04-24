package com.leosatdata.service;

import java.util.Date;
import java.util.Timer;
import java.util.TimerTask;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.leosatdata.model.Event;
import com.leosatdata.model.EventType;
import com.leosatdata.model.State;
import com.leosatdata.util.Configuration;

public class TimerService {

	private final static Logger LOG = LoggerFactory.getLogger(TimerService.class);

	private final State state;
	private final Configuration config;
	private final int localScid;

	private Timer waitTimer;
	private TimerTask waitTimerTask;
	private TimerTask carrierLossTask;
	private TimerTask plcwTask;

	public TimerService(State state, Configuration config) {
		this.state = state;
		this.config = config;
		this.localScid = config.getInteger("local_spacecraft_id", 0);
	}

	public void start() {
		waitTimer = new Timer("wt-" + localScid); // this will start Thread
	}

	public void stop() {
		if (waitTimer != null) {
			waitTimer.cancel();
		}
	}

	public void scheduleWait(EventType type, long duration) {
		waitTimerTask = new TimerTask() {

			@Override
			public void run() {
				sendEvent(new Event(type));
			}
		};
		LOG.info("waiting: {} for {} ms", type, duration);
		waitTimer.schedule(waitTimerTask, duration);
	}

	public void cancelWait() {
		if (waitTimerTask == null) {
			return;
		}
		waitTimerTask.cancel();
		waitTimerTask = null;
	}

	public void scheduleCarrierLossTimeout(EventType type, long duration) {
		if (carrierLossTask != null) {
			return;
		}
		carrierLossTask = new TimerTask() {

			@Override
			public void run() {
				sendEvent(new Event(type));
			}
		};
		LOG.info("carrier loss: {} for {} ms", type, duration);
		waitTimer.schedule(carrierLossTask, duration);
	}

	public void cancelCarrierLoss() {
		if (carrierLossTask == null) {
			return;
		}
		LOG.info("carrier recovered");
		carrierLossTask.cancel();
		carrierLossTask = null;
	}

	public void stopPeriodicPlcw() {
		if (plcwTask == null) {
			return;
		}
		plcwTask.cancel();
		plcwTask = null;
	}

	public void startPeriodicPlcw() {
		long plcwPeriod = config.getDuration("plcw_repeat_interval");
		plcwTask = new TimerTask() {

			@Override
			public void run() {
				sendEvent(new Event(EventType.REQUEST_PLCW));
			}
		};
		waitTimer.schedule(plcwTask, new Date(System.currentTimeMillis() + plcwPeriod), plcwPeriod);
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
